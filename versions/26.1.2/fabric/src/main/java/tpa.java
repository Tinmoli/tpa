package tpa;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import tpa.StorageConvertCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


public class tpa {
	public static String MOD_LOADER;
	public static Path SAVE_DIR;
	public static Path CONFIG_DIR;
	public static Path LANG_DIR;
	public static MinecraftServer SERVER;

	// Built-in language files to export on first run
	private static final String[] BUILTIN_LANGS = {
		"en_us", "zh_cn"
	};
	private static final Gson LANG_GSON = new GsonBuilder()
			.setPrettyPrinting()
			.create();

	// Gets ran when the server starts, initializes the mod :3
	public static void initializeMod(MinecraftServer server) {
		Constants.LOGGER.info("Initializing tpa (V{})! Hello {}!", Constants.VERSION, MOD_LOADER);

		// Static state survives integrated-server restarts in the same JVM.
		// Cancel old timers and requests before binding to the new server.
		TeleportDelayManager.cancelAll();
		TpaCommand.clearRequests();

		SAVE_DIR   = Path.of(String.valueOf(server.getWorldPath(LevelResource.ROOT)));
		CONFIG_DIR = Paths.get(System.getProperty("user.dir")).resolve("config").resolve("tpa");
		LANG_DIR   = CONFIG_DIR.resolve("lang");
		SERVER     = server;

		ConfigManager.ConfigInit();
		StorageManager.StorageInit();
		DeathLocationStorage.clearDeathLocations();

		// Synchronize managed built-in language files on every startup.
		syncBuiltinLangFiles();
	}

	/**
	 * Reloads all mutable runtime state from disk. Pending teleports and TPA
	 * requests are cancelled first so callbacks created with old settings or
	 * player references cannot run after the reload.
	 */
	public static synchronized void reloadRuntimeState() throws Exception {
		if (CONFIG_DIR == null || LANG_DIR == null || SERVER == null) {
			throw new IllegalStateException("TPA has not finished initializing.");
		}

		TeleportDelayManager.cancelAll();
		TpaCommand.clearRequests();
		ConfigManager.ConfigLoader();
		StorageManager.reloadFromSqlite();
		// Death locations are runtime-only player data and must survive a
		// configuration/storage reload.
		syncBuiltinLangFiles();

		// Refresh each connected client's command tree because enabled flags are
		// evaluated by command requirements and may have changed.
		for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
			SERVER.getCommands().sendCommands(player);
		}

		Constants.LOGGER.info(
				"Reloaded TPA configuration, SQLite storage, and language files.");
	}

	/**
	 * Synchronizes zh_cn.json and en_us.json with their bundled key sets.
	 * Existing values are preserved, new bundled keys are added, and keys no
	 * longer present in the bundled file are removed. Other language files are
	 * user-managed and are never modified.
	 */
	private static void syncBuiltinLangFiles() {
		try {
			Files.createDirectories(LANG_DIR);
			for (String lang : BUILTIN_LANGS) {
				syncBuiltinLangFile(lang);
			}
			tools.clearLangCache();
		} catch (Exception e) {
			Constants.LOGGER.error("Failed to synchronize built-in language files! => ", e);
		}
	}

	private static void syncBuiltinLangFile(String lang) throws Exception {
		String resourcePath = String.format(
				"/assets/%s/lang/%s.json", Constants.MOD_ID, lang);
		JsonObject bundled;

		try (InputStream stream = tpa.class.getResourceAsStream(resourcePath)) {
			if (stream == null) {
				Constants.LOGGER.error(
						"Bundled language resource was not found: {}", resourcePath);
				return;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				bundled = JsonParser.parseReader(reader).getAsJsonObject();
			}
		}

		Path destination = LANG_DIR.resolve(lang + ".json");
		JsonObject existing = new JsonObject();
		boolean invalidExistingFile = false;

		if (Files.exists(destination) && Files.size(destination) > 0) {
			try (Reader reader = Files.newBufferedReader(
					destination, StandardCharsets.UTF_8)) {
				existing = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception e) {
				invalidExistingFile = true;
				Path backup = LANG_DIR.resolve(
						lang + ".json.invalid-" + System.currentTimeMillis() + ".bak");
				Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
				Constants.LOGGER.warn(
						"Invalid language file {} was backed up to {} and will be rebuilt.",
						destination, backup);
			}
		}

		JsonObject synchronizedEntries = new JsonObject();
		int added = 0;
		int preserved = 0;

		for (var entry : bundled.entrySet()) {
			String key = entry.getKey();
			JsonElement existingValue = existing.get(key);
			if (existingValue != null && existingValue.isJsonPrimitive()
					&& existingValue.getAsJsonPrimitive().isString()) {
				synchronizedEntries.add(key, existingValue.deepCopy());
				preserved++;
			} else {
				synchronizedEntries.add(key, entry.getValue().deepCopy());
				added++;
			}
		}

		int removed = Math.max(0, existing.size() - preserved);
		boolean changed = invalidExistingFile
				|| !Files.exists(destination)
				|| added > 0
				|| removed > 0;

		if (changed) {
			writeLanguageFileAtomically(destination, synchronizedEntries);
			Constants.LOGGER.info(
					"Synchronized language file {} (added: {}, removed: {}, preserved: {}).",
					lang, added, removed, preserved);
		} else {
			Constants.LOGGER.info(
					"Language file {} is up to date; custom values were preserved.", lang);
		}
	}

	private static void writeLanguageFileAtomically(
			Path destination, JsonObject entries) throws Exception {
		Path temporary = destination.resolveSibling(
				destination.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(
				temporary, StandardCharsets.UTF_8)) {
			LANG_GSON.toJson(entries, writer);
		}

		try {
			Files.move(temporary, destination,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, destination,
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// initialize commands, also allows me to easily disable any when there is a config
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		back.register(dispatcher);
		home.register(dispatcher);
		TpaCommand.register(dispatcher);
		warp.register(dispatcher);
		worldspawn.register(dispatcher);
		tpals.register(dispatcher);
		rtp.register(dispatcher);
		StorageConvertCommand.register(dispatcher);
		ReloadCommand.register(dispatcher);

	}

	// Runs when the playerDeath mixin calls it, updates the /back command position
	public static void onPlayerDeath(ServerPlayer player) {
		BlockPos pos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		String world = player.level().dimension().identifier().toString();
		String uuid  = player.getStringUUID();

		DeathLocationStorage.setDeathLocation(uuid, pos, world);
	}
}
