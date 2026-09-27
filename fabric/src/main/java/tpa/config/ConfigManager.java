package tpa.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import tpa.Constants;
import tpa.TpaMod;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    public static Path CONFIG_FILE;
    public static ModConfig CONFIG;

    public static void initialize() {
        CONFIG_FILE = TpaMod.CONFIG_DIR.resolve("config.yml");
        try {
            load();
        } catch (Exception exception) {
            Constants.LOGGER.error(
                    "Error while initializing the config file! Exiting! => ", exception);
            throw new RuntimeException(
                    "Error while initializing the config file! Exiting!", exception);
        }
    }

    public static void load() throws Exception {
        Files.createDirectories(TpaMod.CONFIG_DIR);
        if (!CONFIG_FILE.toFile().exists() || CONFIG_FILE.toFile().length() == 0) {
            Constants.LOGGER.warn("Config file not found or empty! Creating default config.");
            CONFIG = new ModConfig();
            save();
            Constants.LOGGER.info("Config created successfully!");
            return;
        }
        try (InputStream is = new FileInputStream(CONFIG_FILE.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                Constants.LOGGER.warn("Config file was empty! Loading defaults.");
                CONFIG = new ModConfig();
                save();
                return;
            }
            CONFIG = fromMap(data);
        }
        if (hasMissingConfig(CONFIG_FILE)) {
            Constants.LOGGER.warn("Missing config keys detected! Adding them to config file.");
            save();
            Constants.LOGGER.info("Missing config keys have been automatically added.");
        } else {
            refreshRtpComments();
            Constants.LOGGER.info("Config loaded successfully!");
        }
    }

    public static void save() throws Exception {
        ModConfig config = CONFIG;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("language", config.language);
        LinkedHashMap<String, Object> back = new LinkedHashMap<>();
        back.put("enabled", config.back.enabled);
        back.put("deleteAfterTeleport", config.back.deleteAfterTeleport);
        root.put("back", back);
        LinkedHashMap<String, Object> home = new LinkedHashMap<>();
        home.put("enabled", config.home.enabled);
        home.put("playerMaximum", config.home.playerMaximum);
        home.put("deleteInvalid", config.home.deleteInvalid);
        home.put("delay", config.home.delay);
        root.put("home", home);
        LinkedHashMap<String, Object> tpaSection = new LinkedHashMap<>();
        tpaSection.put("enabled", config.tpa.enabled);
        tpaSection.put("delay", config.tpa.delay);
        tpaSection.put("cancelOnMove", config.tpa.cancelOnMove);
        tpaSection.put("requestExpireReminder", config.tpa.requestExpireReminder);
        root.put("tpa", tpaSection);
        LinkedHashMap<String, Object> warp = new LinkedHashMap<>();
        warp.put("enabled", config.warp.enabled);
        warp.put("deleteInvalid", config.warp.deleteInvalid);
        root.put("warp", warp);
        LinkedHashMap<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("enabled", config.spawn.enabled);
        spawn.put("world_id", config.spawn.world_id);
        root.put("spawn", spawn);
        LinkedHashMap<String, Object> rtp = new LinkedHashMap<>();
        rtp.put("enabled", config.rtp.enabled);
        rtp.put("minRange", config.rtp.minRange);
        rtp.put("maxRange", config.rtp.maxRange);
        rtp.put("cooldownEnabled", config.rtp.cooldownEnabled);
        rtp.put("cooldownSeconds", config.rtp.cooldownSeconds);
        rtp.put("maxConcurrentLoads", config.rtp.maxConcurrentLoads);
        rtp.put("failureCooldownSeconds", config.rtp.failureCooldownSeconds);
        rtp.put("maxAttempts", config.rtp.maxAttempts);
        rtp.put("timeoutSeconds", config.rtp.timeoutSeconds);
        rtp.put("loadTimeoutSeconds", config.rtp.loadTimeoutSeconds);
        rtp.put("invulnerabilityTicks", config.rtp.invulnerabilityTicks);
        rtp.put("biomeBlacklist", config.rtp.biomeBlacklist);
        rtp.put("floorBlacklist", config.rtp.floorBlacklist);
        Map<String, Object> dimensions = new LinkedHashMap<>();
        config.rtp.dimensions.forEach(
                (id, dimensionConfig) -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("mode", dimensionConfig.mode);
                    if (dimensionConfig.centerX != null)
                        values.put("centerX", dimensionConfig.centerX);
                    if (dimensionConfig.centerZ != null)
                        values.put("centerZ", dimensionConfig.centerZ);
                    if (dimensionConfig.minRange != null)
                        values.put("minRange", dimensionConfig.minRange);
                    if (dimensionConfig.maxRange != null)
                        values.put("maxRange", dimensionConfig.maxRange);
                    if (dimensionConfig.biomeBlacklist != null)
                        values.put("biomeBlacklist", dimensionConfig.biomeBlacklist);
                    if (dimensionConfig.floorBlacklist != null)
                        values.put("floorBlacklist", dimensionConfig.floorBlacklist);
                    dimensions.put(id, values);
                });
        rtp.put("dimensions", dimensions);
        root.put("rtp", rtp);
        Files.createDirectories(CONFIG_FILE.getParent());
        StringWriter yamlWriter = new StringWriter();
        yaml.dump(root, yamlWriter);
        String raw = insertComments(yamlWriter.toString());
        try (Writer writer =
                new OutputStreamWriter(
                        new FileOutputStream(CONFIG_FILE.toFile()),
                        java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("# TPA 模组配置文件\n" + "# 修改后执行 /tpareload 生效，也可以重启服务器\n\n");
            writer.write(raw);
        }
    }

    private static String insertComments(String yaml) {
        String[][] rules = {
            {"language:", "# 语言设置，可选值：zh_cn、en_us"},
            {"back:", "# /back 命令配置"},
            {"home:", "# /home 命令配置"},
            {"tpa:", "# /tpa 命令配置"},
            {"warp:", "# /warp 命令配置"},
            {"spawn:", "# /spawn 命令配置"},
            {"rtp:", "# /rtp 命令配置"},
            {"  enabled:", "  # 是否启用该命令"},
            {"  deleteAfterTeleport:", "  # 传送后是否删除死亡位置记录"},
            {"  playerMaximum:", "  # 每位玩家最多可以设置的家的数量；0 表示禁止新增"},
            {"  deleteInvalid:", "  # 是否自动删除世界不存在的无效位置"},
            {"  delay:", "  # 传送等待时间（秒），0 表示立即传送"},
            {"  cancelOnMove:", "  # 传送等待期间移动是否取消传送"},
            {"  requestExpireReminder:", "  # 请求过期前多少秒提醒；请求固定 120 秒过期，0 表示不提醒"},
            {"  world_id:", "  # 出生点所在世界的 ID，默认为主世界"},
            {"  minRange:", "  # 随机传送最小范围（方块）"},
            {"  maxRange:", "  # 随机传送最大范围（方块）"},
            {"  cooldownEnabled:", "  # 是否启用 RTP 请求冷却"},
            {"  cooldownSeconds:", "  # RTP 成功后的冷却时间（秒），0 表示不冷却"},
            {"  maxConcurrentLoads:", "  # 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求"},
            {"  failureCooldownSeconds:", "  # RTP 最终失败或取消后的冷却秒数，0 表示不冷却"},
            {"  maxAttempts:", "  # 每个请求最多尝试的随机位置数量（1-100）"},
            {"  timeoutSeconds:", "  # 寻找安全位置的总超时时间（秒，1-120）"},
            {"  loadTimeoutSeconds:", "  # 单次区块加载的超时时间（秒，1-30）"},
            {"  invulnerabilityTicks:", "  # 传送后的普通伤害保护时长（tick，0-1200），0 表示关闭"},
            {"  biomeBlacklist:", "  # 禁用群系：支持完整 ID 和以 # 开头的标签（标签需加引号）"},
            {"  floorBlacklist:", "  # 危险方块：支持完整 ID 和标签；液体、树叶、基岩始终禁止"},
            {
                "  dimensions:",
                "  # 按维度 ID 覆盖 mode(auto/surface/interior)、centerX/Z、minRange/maxRange 及黑名单"
            },
        };
        StringBuilder sb = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            for (String[] rule : rules) {
                if (line.startsWith(rule[0])) {
                    sb.append(rule[1]).append("\n");
                    break;
                }
            }
            sb.append(line).append("\n");
        }
        String result = sb.toString();
        while (result.endsWith("\n\n")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static boolean hasMissingConfig(Path configFile) throws Exception {
        try (InputStream is = new FileInputStream(configFile.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null) return true;
            // SQLite is now the only backend. Rewrite old configs once to
            // remove the obsolete storage.backend section. Also inspect nested
            // keys so incremental upgrades really add newly introduced options.
            return data.containsKey("storage")
                    || !hasKeys(data, "back", "enabled", "deleteAfterTeleport")
                    || !hasKeys(data, "home", "enabled", "playerMaximum", "deleteInvalid", "delay")
                    || !hasKeys(
                            data,
                            "tpa",
                            "enabled",
                            "delay",
                            "cancelOnMove",
                            "requestExpireReminder")
                    || !hasKeys(data, "warp", "enabled", "deleteInvalid")
                    || !hasKeys(data, "spawn", "enabled", "world_id")
                    || !hasKeys(
                            data,
                            "rtp",
                            "enabled",
                            "minRange",
                            "maxRange",
                            "cooldownEnabled",
                            "cooldownSeconds",
                            "maxConcurrentLoads",
                            "failureCooldownSeconds",
                            "maxAttempts",
                            "timeoutSeconds",
                            "loadTimeoutSeconds",
                            "invulnerabilityTicks",
                            "biomeBlacklist",
                            "floorBlacklist",
                            "dimensions");
        }
    }

    /** Refresh only known generated comments; do not rewrite values or user comments. */
    private static void refreshRtpComments() throws Exception {
        String original = Files.readString(CONFIG_FILE, java.nio.charset.StandardCharsets.UTF_8);
        String text = original;
        String[][] changes = {
            {"# RTP 成功后的冷却秒数，0 表示成功后不冷却；保留旧配置值", "# RTP 成功后的冷却时间（秒），0 表示不冷却"},
            {"# RTP 请求冷却秒数，0 表示不冷却", "# RTP 成功后的冷却时间（秒），0 表示不冷却"},
            {"# 全服 RTP 请求并发上限（1-8），默认 2；满额直接拒绝，提高会增加内存压力", "# 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求"},
            {"# 全服 RTP 区块加载并发上限（1-8），默认 1；提高会增加内存压力", "# 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求"},
            {"# 全服同时进行的 RTP 请求数量上限（1-8），满额时拒绝新请求", "# 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求"},
            {"# 每个请求最多尝试的随机列数（1-100），默认 10", "# 每个请求最多尝试的随机位置数量（1-100）"},
            {"# 请求总超时秒数（1-120），默认 15；超时不会继续传送", "# 寻找安全位置的总超时时间（秒，1-120）"},
            {"# 单次区块加载超时秒数（1-30），默认 5；旧任务收尾后才允许重试", "# 单次区块加载的超时时间（秒，1-30）"},
            {"# 传送后普通伤害保护 tick 数（0-1200），默认 60；不拦截绕过无敌的伤害", "# 传送后的普通伤害保护时长（tick，0-1200），0 表示关闭"}
        };
        for (String[] change : changes) text = text.replace(change[0], change[1]);
        if (!text.equals(original))
            Files.writeString(CONFIG_FILE, text, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasKeys(Map<String, Object> root, String section, String... keys) {
        Object value = root.get(section);
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        for (String key : keys) {
            if (!map.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static ModConfig fromMap(Map<String, Object> data) {
        ModConfig config = new ModConfig();
        if (data.containsKey("language")) config.language = (String) data.get("language");
        if (data.containsKey("back")) {
            Map<String, Object> backSection = (Map<String, Object>) data.get("back");
            if (backSection.containsKey("enabled"))
                config.back.enabled = (boolean) backSection.get("enabled");
            if (backSection.containsKey("deleteAfterTeleport"))
                config.back.deleteAfterTeleport = (boolean) backSection.get("deleteAfterTeleport");
        }
        if (data.containsKey("home")) {
            Map<String, Object> homeSection = (Map<String, Object>) data.get("home");
            if (homeSection.containsKey("enabled"))
                config.home.enabled = (boolean) homeSection.get("enabled");
            if (homeSection.containsKey("playerMaximum"))
                config.home.playerMaximum = (int) homeSection.get("playerMaximum");
            if (homeSection.containsKey("deleteInvalid"))
                config.home.deleteInvalid = (boolean) homeSection.get("deleteInvalid");
            if (homeSection.containsKey("delay"))
                config.home.delay = (int) homeSection.get("delay");
        }
        if (data.containsKey("tpa")) {
            Map<String, Object> tpaSection = (Map<String, Object>) data.get("tpa");
            if (tpaSection.containsKey("enabled"))
                config.tpa.enabled = (boolean) tpaSection.get("enabled");
            if (tpaSection.containsKey("delay")) config.tpa.delay = (int) tpaSection.get("delay");
            if (tpaSection.containsKey("cancelOnMove"))
                config.tpa.cancelOnMove = (boolean) tpaSection.get("cancelOnMove");
            if (tpaSection.containsKey("requestExpireReminder"))
                config.tpa.requestExpireReminder = (int) tpaSection.get("requestExpireReminder");
        }
        if (data.containsKey("warp")) {
            Map<String, Object> warpSection = (Map<String, Object>) data.get("warp");
            if (warpSection.containsKey("enabled"))
                config.warp.enabled = (boolean) warpSection.get("enabled");
            if (warpSection.containsKey("deleteInvalid"))
                config.warp.deleteInvalid = (boolean) warpSection.get("deleteInvalid");
        }
        if (data.containsKey("spawn")) {
            Map<String, Object> spawnSection = (Map<String, Object>) data.get("spawn");
            if (spawnSection.containsKey("enabled"))
                config.spawn.enabled = (boolean) spawnSection.get("enabled");
            if (spawnSection.containsKey("world_id"))
                config.spawn.world_id = (String) spawnSection.get("world_id");
        }
        if (data.containsKey("rtp")) {
            Map<String, Object> rtpSection = (Map<String, Object>) data.get("rtp");
            if (rtpSection.containsKey("enabled"))
                config.rtp.enabled = (boolean) rtpSection.get("enabled");
            if (rtpSection.containsKey("minRange"))
                config.rtp.minRange = (int) rtpSection.get("minRange");
            if (rtpSection.containsKey("maxRange"))
                config.rtp.maxRange = (int) rtpSection.get("maxRange");
            if (rtpSection.containsKey("cooldownEnabled"))
                config.rtp.cooldownEnabled = (boolean) rtpSection.get("cooldownEnabled");
            if (rtpSection.containsKey("cooldownSeconds"))
                config.rtp.cooldownSeconds =
                        Math.max(0, ((Number) rtpSection.get("cooldownSeconds")).intValue());
            config.rtp.maxConcurrentLoads = number(rtpSection, "maxConcurrentLoads", 10, 1, 10);
            config.rtp.failureCooldownSeconds =
                    number(rtpSection, "failureCooldownSeconds", 30, 0, 86400);
            config.rtp.maxAttempts = number(rtpSection, "maxAttempts", 10, 1, 100);
            config.rtp.timeoutSeconds = number(rtpSection, "timeoutSeconds", 15, 1, 120);
            config.rtp.loadTimeoutSeconds = number(rtpSection, "loadTimeoutSeconds", 5, 1, 30);
            config.rtp.invulnerabilityTicks =
                    number(rtpSection, "invulnerabilityTicks", 60, 0, 1200);
            config.rtp.biomeBlacklist =
                    strings(rtpSection.get("biomeBlacklist"), config.rtp.biomeBlacklist);
            config.rtp.floorBlacklist =
                    strings(rtpSection.get("floorBlacklist"), config.rtp.floorBlacklist);
            if (rtpSection.get("dimensions") instanceof Map<?, ?> dimensionSettings)
                dimensionSettings.forEach(
                        (id, raw) -> {
                            if (!(raw instanceof Map<?, ?> values)) return;
                            ModConfig.Rtp.Dimension dimensionConfig = new ModConfig.Rtp.Dimension();
                            if (values.get("mode") instanceof String mode
                                    && List.of("auto", "surface", "interior").contains(mode))
                                dimensionConfig.mode = mode;
                            if (values.containsKey("centerX"))
                                dimensionConfig.centerX =
                                        number(values, "centerX", 0, -29999984, 29999984);
                            if (values.containsKey("centerZ"))
                                dimensionConfig.centerZ =
                                        number(values, "centerZ", 0, -29999984, 29999984);
                            if (values.containsKey("minRange"))
                                dimensionConfig.minRange =
                                        number(values, "minRange", 0, 0, 29999984);
                            if (values.containsKey("maxRange"))
                                dimensionConfig.maxRange =
                                        number(values, "maxRange", 2000, 0, 29999984);
                            if (values.containsKey("biomeBlacklist"))
                                dimensionConfig.biomeBlacklist =
                                        strings(
                                                values.get("biomeBlacklist"),
                                                config.rtp.biomeBlacklist);
                            if (values.containsKey("floorBlacklist"))
                                dimensionConfig.floorBlacklist =
                                        strings(
                                                values.get("floorBlacklist"),
                                                config.rtp.floorBlacklist);
                            config.rtp.dimensions.put(String.valueOf(id), dimensionConfig);
                        });
        }
        if (config.language == null || config.language.isBlank()) {
            Constants.LOGGER.warn("language cannot be empty; using zh_cn.");
            config.language = "zh_cn";
        }
        if (config.spawn.world_id == null || config.spawn.world_id.isBlank()) {
            Constants.LOGGER.warn("spawn.world_id cannot be empty; using minecraft:overworld.");
            config.spawn.world_id = "minecraft:overworld";
        }
        if (config.home.playerMaximum < 0) {
            Constants.LOGGER.warn("home.playerMaximum cannot be negative; using 0.");
            config.home.playerMaximum = 0;
        }
        if (config.home.delay < 0) {
            Constants.LOGGER.warn("home.delay cannot be negative; using 0.");
            config.home.delay = 0;
        }
        if (config.tpa.delay < 0) {
            Constants.LOGGER.warn("tpa.delay cannot be negative; using 0.");
            config.tpa.delay = 0;
        }
        if (config.tpa.requestExpireReminder < 0) {
            Constants.LOGGER.warn("tpa.requestExpireReminder cannot be negative; using 0.");
            config.tpa.requestExpireReminder = 0;
        } else if (config.tpa.requestExpireReminder > 120) {
            Constants.LOGGER.warn(
                    "tpa.requestExpireReminder cannot exceed the 120-second request lifetime; using"
                            + " 120.");
            config.tpa.requestExpireReminder = 120;
        }

        final int worldBorderLimit = 29_999_984;
        if (config.rtp.minRange < 0) {
            Constants.LOGGER.warn("rtp.minRange cannot be negative; using 0.");
            config.rtp.minRange = 0;
        }
        if (config.rtp.minRange > worldBorderLimit) {
            Constants.LOGGER.warn(
                    "rtp.minRange exceeds the world border; using {}.", worldBorderLimit);
            config.rtp.minRange = worldBorderLimit;
        }
        if (config.rtp.maxRange < config.rtp.minRange) {
            Constants.LOGGER.warn(
                    "rtp.maxRange cannot be less than rtp.minRange; using {}.",
                    config.rtp.minRange);
            config.rtp.maxRange = config.rtp.minRange;
        }
        if (config.rtp.maxRange > worldBorderLimit) {
            Constants.LOGGER.warn(
                    "rtp.maxRange exceeds the world border; using {}.", worldBorderLimit);
            config.rtp.maxRange = worldBorderLimit;
        }
        return config;
    }

    private static int number(Map<?, ?> map, String key, int fallback, int min, int max) {
        Object value = map.get(key);
        return value instanceof Number number
                ? (int) Math.max(min, Math.min(max, number.longValue()))
                : fallback;
    }

    private static List<String> strings(Object raw, List<String> fallback) {
        if (!(raw instanceof List<?> list)) return new ArrayList<>(fallback);
        List<String> result = new ArrayList<>();
        for (Object item : list)
            if (item instanceof String text && !text.isBlank()) result.add(text);
        return result;
    }
}
