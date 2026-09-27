package tpa.language;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import tpa.Constants;
import tpa.TpaMod;
import tpa.config.ConfigManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageManager {
    private static final ConcurrentHashMap<String, Map<String, String>> langCache =
            new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(\\d+)%");
    private static final String[] BUILTIN_LANGS = {"en_us", "zh_cn"};
    private static final Gson LANG_GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void clearLangCache() {
        langCache.clear();
    }

    public static MutableComponent getTranslatedText(
            String key, ServerPlayer player, MutableComponent... args) {
        String configuredLang =
                (ConfigManager.CONFIG != null)
                        ? ConfigManager.CONFIG.language.toLowerCase()
                        : "zh_cn";

        // 1. Try configured language (uses cache)
        String value = getFromCache(configuredLang, key);

        // 2. Fallback to en_us
        if (value == null && !configuredLang.equals("en_us")) {
            value = getFromCache("en_us", key);
        }

        if (value == null) {
            Constants.LOGGER.error(
                    "Key \"{}\" not found in any language file in lang/ directory, sending raw key"
                            + " as fallback.",
                    key);
            return Component.literal(key);
        }

        return buildComponent(value, PLACEHOLDER_PATTERN, args);
    }

    private static String getFromCache(String lang, String key) {
        Map<String, String> entries = langCache.computeIfAbsent(lang, l -> loadLangFile(l));
        return entries.get(key);
    }

    private static Map<String, String> loadLangFile(String lang) {
        try {
            Path langFile = TpaMod.LANG_DIR.resolve(lang + ".json");
            if (!langFile.toFile().exists()) return Collections.emptyMap();

            try (Reader reader =
                    new InputStreamReader(
                            new FileInputStream(langFile.toFile()), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    map.put(entry.getKey(), entry.getValue().getAsString());
                }
                return Collections.unmodifiableMap(map);
            }
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to load language file for '{}': ", lang, e);
            return Collections.emptyMap();
        }
    }

    private static MutableComponent buildComponent(
            String translation, Pattern pattern, MutableComponent... args) {
        Matcher matcher = pattern.matcher(translation);
        MutableComponent component = Component.literal("");
        int lastIndex = 0;

        while (matcher.find()) {
            component.append(Component.literal(translation.substring(lastIndex, matcher.start())));
            int index = Integer.parseInt(matcher.group(1));
            if (args != null && index < args.length) {
                component.append(args[index]);
            }
            lastIndex = matcher.end();
        }
        component.append(translation.substring(lastIndex));
        return component;
    }

    public static void syncBuiltinLangFiles() {
        try {
            Files.createDirectories(TpaMod.LANG_DIR);
            for (String lang : BUILTIN_LANGS) {
                syncBuiltinLangFile(lang);
            }
            LanguageManager.clearLangCache();
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to synchronize built-in language files! => ", e);
        }
    }

    private static void syncBuiltinLangFile(String lang) throws Exception {
        String resourcePath = String.format("/assets/%s/lang/%s.json", Constants.MOD_ID, lang);
        JsonObject bundled;

        try (InputStream stream = TpaMod.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                Constants.LOGGER.error("Bundled language resource was not found: {}", resourcePath);
                return;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                bundled = JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        Path destination = TpaMod.LANG_DIR.resolve(lang + ".json");
        JsonObject existing = new JsonObject();
        boolean invalidExistingFile = false;

        if (Files.exists(destination) && Files.size(destination) > 0) {
            try (Reader reader = Files.newBufferedReader(destination, StandardCharsets.UTF_8)) {
                existing = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                invalidExistingFile = true;
                Path backup =
                        TpaMod.LANG_DIR.resolve(
                                lang + ".json.invalid-" + System.currentTimeMillis() + ".bak");
                Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
                Constants.LOGGER.warn(
                        "Invalid language file {} was backed up to {} and will be rebuilt.",
                        destination,
                        backup);
            }
        }

        JsonObject synchronizedEntries = new JsonObject();
        int added = 0;
        int preserved = 0;

        for (var entry : bundled.entrySet()) {
            String key = entry.getKey();
            JsonElement existingValue = existing.get(key);
            if (existingValue != null
                    && existingValue.isJsonPrimitive()
                    && existingValue.getAsJsonPrimitive().isString()) {
                synchronizedEntries.add(key, existingValue.deepCopy());
                preserved++;
            } else {
                synchronizedEntries.add(key, entry.getValue().deepCopy());
                added++;
            }
        }

        int removed = Math.max(0, existing.size() - preserved);
        boolean changed =
                invalidExistingFile || !Files.exists(destination) || added > 0 || removed > 0;

        if (changed) {
            writeLanguageFileAtomically(destination, synchronizedEntries);
            Constants.LOGGER.info(
                    "Synchronized language file {} (added: {}, removed: {}, preserved: {}).",
                    lang,
                    added,
                    removed,
                    preserved);
        } else {
            Constants.LOGGER.info(
                    "Language file {} is up to date; custom values were preserved.", lang);
        }
    }

    private static void writeLanguageFileAtomically(Path destination, JsonObject entries)
            throws Exception {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            LANG_GSON.toJson(entries, writer);
        }

        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
