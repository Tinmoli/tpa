package tpa;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;

public class tools {

    public static void sendPlayerMessage(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }

    public static void sendPlayerMessage(ServerPlayer player, Component message, boolean overlay) {
        if (overlay) {
            // 26.1 Action Bar: 发送客户端包
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        } else {
            player.sendSystemMessage(message);
        }
    }

    private static final Set<String> unsafeCollisionFreeBlocks = Set.of(
            "block.minecraft.lava", "block.minecraft.flowing_lava",
            "block.minecraft.end_portal", "block.minecraft.end_gateway",
            "block.minecraft.fire", "block.minecraft.soul_fire",
            "block.minecraft.powder_snow", "block.minecraft.nether_portal"
    );

    // 语言文件内存缓存：lang -> key -> value
    // 避免每次发送消息都读磁盘文件
    private static final ConcurrentHashMap<String, Map<String, String>> langCache = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(\\d+)%");

    /** 清空语言缓存（语言切换/重载时调用） */
    public static void clearLangCache() {
        langCache.clear();
    }

    // -------------------------------------------------------------------------

    public static void Teleporter(ServerPlayer player, ServerLevel world, Vec3 coords) {
        // 传送前粒子 + 末影人音效
        spawnEnchantBurst(world, player.getX(), player.getY() + 1.6, player.getZ(), 40);
        world.playSound(null, player.blockPosition(), SoundEvent.createVariableRangeEvent(ENDERMAN_TELEPORT.location()), SoundSource.PLAYERS, 0.4f, 1.0f);

        boolean flying = player.getAbilities().flying;
        player.teleportTo(world, coords.x, coords.y, coords.z, Set.of(), player.getYRot(), player.getXRot(), false);
        if (flying) { player.getAbilities().flying = true; player.onUpdateAbilities(); }

        // 传送完成：末影人音效
        world.playSound(null, player.blockPosition(), SoundEvent.createVariableRangeEvent(ENDERMAN_TELEPORT.location()), SoundSource.PLAYERS, 0.6f, 1.0f);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                spawnEnchantBurst(world, player.getX(), player.getY() + 1.6, player.getZ(), 40);
            }
        }, 100);
    }

    /** 接受请求时播放经验球音效 */
    public static void playAcceptSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** 拒绝请求时无音效 */
    public static void playDenySound(ServerPlayer player) {
        // 无音效
    }

    /** 在指定位置以半径 0.5 格球体内随机分布发射 enchant 粒子，向下飘落 */
    private static void spawnEnchantBurst(ServerLevel world, double cx, double cy, double cz, int count) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double theta = rng.nextDouble() * 2 * Math.PI;
            double phi   = rng.nextDouble() * Math.PI;
            double r     = 0.5 * Math.cbrt(rng.nextDouble());
            double ox = r * Math.sin(phi) * Math.cos(theta);
            double oy = r * Math.cos(phi);
            double oz = r * Math.sin(phi) * Math.sin(theta);
            double vx = (rng.nextDouble() - 0.5) * 0.04;
            double vy = -0.04 - rng.nextDouble() * 0.06;
            double vz = (rng.nextDouble() - 0.5) * 0.04;
            world.sendParticles(ParticleTypes.ENCHANT, cx + ox, cy + oy, cz + oz, 1, vx, vy, vz, 1.0);
        }
    }

    // checks a 7x7x7 location around the player in order to find a safe place to teleport them to.
    public static Optional<BlockPos> getSafeBlockPos(BlockPos blockPos, ServerLevel world) {
        int row = 1;
        int rows = 3;

        int blockPosX = blockPos.getX();
        int blockPosY = blockPos.getY();
        int blockPosZ = blockPos.getZ();

        if (isBlockPosSafe(blockPos, world)) {
            return Optional.of(blockPos);
        } else {
            while (row <= rows) {
                for (int z = -row; z <= row; z++) {
                    for (int x = -row; x <= row; x++) {
                        for (int y = -row; y <= row; y++) {
                            if ((x == -row || x == row) || (z == -row || z == row) || (y == -row || y == row)) {
                                BlockPos newPos = new BlockPos(blockPosX + x, blockPosY + y, blockPosZ + z);
                                if (isBlockPosSafe(newPos, world)) {
                                    return Optional.of(newPos);
                                }
                            }
                        }
                    }
                }
                row++;
            }
            return Optional.empty();
        }
    }

    /**
     * Gets translated text for a key.
     * Reads from config/tpa/lang/ with in-memory caching to avoid repeated disk I/O.
     * Priority:
     *   1. config/tpa/lang/{configured_language}.json
     *   2. config/tpa/lang/en_us.json  (fallback)
     */
    public static MutableComponent getTranslatedText(String key, ServerPlayer player, MutableComponent... args) {
        String configuredLang = (ConfigManager.CONFIG != null)
                ? ConfigManager.CONFIG.language.toLowerCase()
                : "zh_cn";

        // 1. Try configured language (uses cache)
        String value = getFromCache(configuredLang, key);

        // 2. Fallback to en_us
        if (value == null && !configuredLang.equals("en_us")) {
            value = getFromCache("en_us", key);
        }

        if (value == null) {
            Constants.LOGGER.error("Key \"{}\" not found in any language file in lang/ directory, sending raw key as fallback.", key);
            return Component.literal(key);
        }

        return buildComponent(value, PLACEHOLDER_PATTERN, args);
    }

    /**
     * Gets a value from the language cache, loading from disk if not yet cached.
     * Returns null if the key doesn't exist in the language file.
     */
    private static String getFromCache(String lang, String key) {
        Map<String, String> entries = langCache.computeIfAbsent(lang, l -> loadLangFile(l));
        return entries.get(key);
    }

    /** Loads a language file from disk into a Map. Returns an empty map on failure. */
    private static Map<String, String> loadLangFile(String lang) {
        try {
            Path langFile = tpa.LANG_DIR.resolve(lang + ".json");
            if (!langFile.toFile().exists()) return Collections.emptyMap();

            try (Reader reader = new InputStreamReader(new FileInputStream(langFile.toFile()), StandardCharsets.UTF_8)) {
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

    /** Build a MutableComponent from a translation string, substituting %n% placeholders */
    private static MutableComponent buildComponent(String translation, Pattern pattern, MutableComponent... args) {
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

    // Gets the ids of all the worlds
    public static List<String> getWorldIds() {
        return StreamSupport.stream(tpa.SERVER.getAllLevels().spliterator(), false)
                .map(level -> level.dimension().identifier().toString())
                .collect(java.util.stream.Collectors.toList());
    }

    // checks if a BlockPos is safe, used by the teleportSafetyChecker.
    private static boolean isBlockPosSafe(BlockPos bottomPlayer, ServerLevel world) {
        BlockPos belowPlayer = new BlockPos(bottomPlayer.getX(), bottomPlayer.getY() - 1, bottomPlayer.getZ());
        String belowPlayerId = world.getBlockState(belowPlayer).getBlock().getDescriptionId();

        String BottomPlayerId = world.getBlockState(bottomPlayer).getBlock().getDescriptionId();

        BlockPos TopPlayer = new BlockPos(bottomPlayer.getX(), bottomPlayer.getY() + 1, bottomPlayer.getZ());
        String TopPlayerId = world.getBlockState(TopPlayer).getBlock().getDescriptionId();

        return (belowPlayerId.equals("block.minecraft.water") || !world.getBlockState(belowPlayer).getCollisionShape(world, belowPlayer).isEmpty())
                && (world.getBlockState(bottomPlayer).getCollisionShape(world, bottomPlayer).isEmpty() && !unsafeCollisionFreeBlocks.contains(BottomPlayerId))
                && (!unsafeCollisionFreeBlocks.contains(TopPlayerId));
    }
}
