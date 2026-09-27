package tpa;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ConfigManager {
    public static Path CONFIG_FILE;
    public static ConfigClass CONFIG;

    public static void ConfigInit() {
        CONFIG_FILE = tpa.CONFIG_DIR.resolve("config.yml");
        try {
            ConfigLoader();
        } catch (Exception e) {
            Constants.LOGGER.error("Error while initializing the config file! Exiting! => ", e);
            throw new RuntimeException("Error while initializing the config file! Exiting!", e);
        }
    }

    public static void ConfigLoader() throws Exception {
        Files.createDirectories(tpa.CONFIG_DIR);
        if (!CONFIG_FILE.toFile().exists() || CONFIG_FILE.toFile().length() == 0) {
            Constants.LOGGER.warn("Config file not found or empty! Creating default config.");
            CONFIG = new ConfigClass();
            ConfigSaver();
            Constants.LOGGER.info("Config created successfully!");
            return;
        }
        try (InputStream is = new FileInputStream(CONFIG_FILE.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data == null) {
                Constants.LOGGER.warn("Config file was empty! Loading defaults.");
                CONFIG = new ConfigClass();
                ConfigSaver();
                return;
            }
            CONFIG = fromMap(data);
        }
        if (hasMissingConfig(CONFIG_FILE)) {
            Constants.LOGGER.warn("Missing config keys detected! Adding them to config file.");
            ConfigSaver();
            Constants.LOGGER.info("Missing config keys have been automatically added.");
        } else {
            refreshRtpComments();
            Constants.LOGGER.info("Config loaded successfully!");
        }
    }

    public static void ConfigSaver() throws Exception {
        ConfigClass cfg = CONFIG;
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("language", cfg.language);
        LinkedHashMap<String, Object> back = new LinkedHashMap<>();
        back.put("enabled", cfg.back.enabled);
        back.put("deleteAfterTeleport", cfg.back.deleteAfterTeleport);
        root.put("back", back);
        LinkedHashMap<String, Object> home = new LinkedHashMap<>();
        home.put("enabled", cfg.home.enabled);
        home.put("playerMaximum", cfg.home.playerMaximum);
        home.put("deleteInvalid", cfg.home.deleteInvalid);
        home.put("delay", cfg.home.delay);
        root.put("home", home);
        LinkedHashMap<String, Object> tpaSection = new LinkedHashMap<>();
        tpaSection.put("enabled", cfg.tpa.enabled);
        tpaSection.put("delay", cfg.tpa.delay);
        tpaSection.put("cancelOnMove", cfg.tpa.cancelOnMove);
        tpaSection.put("requestExpireReminder", cfg.tpa.requestExpireReminder);
        root.put("tpa", tpaSection);
        LinkedHashMap<String, Object> warp = new LinkedHashMap<>();
        warp.put("enabled", cfg.warp.enabled);
        warp.put("deleteInvalid", cfg.warp.deleteInvalid);
        root.put("warp", warp);
        LinkedHashMap<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("enabled", cfg.spawn.enabled);
        spawn.put("world_id", cfg.spawn.world_id);
        root.put("spawn", spawn);
        LinkedHashMap<String, Object> rtp = new LinkedHashMap<>();
        rtp.put("enabled", cfg.rtp.enabled);
        rtp.put("minRange", cfg.rtp.minRange);
        rtp.put("maxRange", cfg.rtp.maxRange);
        rtp.put("cooldownEnabled", cfg.rtp.cooldownEnabled);
        rtp.put("cooldownSeconds", cfg.rtp.cooldownSeconds);
        rtp.put("maxConcurrentLoads", cfg.rtp.maxConcurrentLoads);
        rtp.put("failureCooldownSeconds", cfg.rtp.failureCooldownSeconds);
        rtp.put("maxAttempts", cfg.rtp.maxAttempts);
        rtp.put("timeoutSeconds", cfg.rtp.timeoutSeconds);
        rtp.put("loadTimeoutSeconds", cfg.rtp.loadTimeoutSeconds);
        rtp.put("invulnerabilityTicks", cfg.rtp.invulnerabilityTicks);
        rtp.put("biomeBlacklist", cfg.rtp.biomeBlacklist);
        rtp.put("floorBlacklist", cfg.rtp.floorBlacklist);
        Map<String, Object> dimensions = new LinkedHashMap<>();
        cfg.rtp.dimensions.forEach((id, d) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("mode", d.mode);
            if (d.centerX != null) values.put("centerX", d.centerX);
            if (d.centerZ != null) values.put("centerZ", d.centerZ);
            if (d.minRange != null) values.put("minRange", d.minRange);
            if (d.maxRange != null) values.put("maxRange", d.maxRange);
            if (d.biomeBlacklist != null) values.put("biomeBlacklist", d.biomeBlacklist);
            if (d.floorBlacklist != null) values.put("floorBlacklist", d.floorBlacklist);
            dimensions.put(id, values);
        });
        rtp.put("dimensions", dimensions);
        root.put("rtp", rtp);
        Files.createDirectories(CONFIG_FILE.getParent());
        StringWriter sw = new StringWriter();
        yaml.dump(root, sw);
        String raw = insertComments(sw.toString());
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(CONFIG_FILE.toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("# TPA 模组配置文件\n"
                    + "# 修改后执行 /tpareload 生效，也可以重启服务器\n\n");
            writer.write(raw);
        }
    }

    private static String insertComments(String yaml) {
        String[][] rules = {
            {"language:",              "# 语言设置，可选值：zh_cn、en_us"},
            {"back:",                  "# /back 命令配置"},
            {"home:",                  "# /home 命令配置"},
            {"tpa:",                   "# /tpa 命令配置"},
            {"warp:",                  "# /warp 命令配置"},
            {"spawn:",                 "# /spawn 命令配置"},
            {"rtp:",                   "# /rtp 命令配置"},
            {"  enabled:",             "  # 是否启用该命令"},
            {"  deleteAfterTeleport:", "  # 传送后是否删除死亡位置记录"},
            {"  playerMaximum:",       "  # 每位玩家最多可以设置的家的数量；0 表示禁止新增"},
            {"  deleteInvalid:",       "  # 是否自动删除世界不存在的无效位置"},
            {"  delay:",               "  # 传送等待时间（秒），0 表示立即传送"},
            {"  cancelOnMove:",        "  # 传送等待期间移动是否取消传送"},
            {"  requestExpireReminder:", "  # 请求过期前多少秒提醒；请求固定 120 秒过期，0 表示不提醒"},
            {"  world_id:",            "  # 出生点所在世界的 ID，默认为主世界"},
            {"  minRange:",            "  # 随机传送最小范围（方块）"},
            {"  maxRange:",            "  # 随机传送最大范围（方块）"},
            {"  cooldownEnabled:",     "  # 是否启用 RTP 请求冷却"},
            {"  cooldownSeconds:",     "  # RTP 成功后的冷却时间（秒），0 表示不冷却"},
            {"  maxConcurrentLoads:",  "  # 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求"},
            {"  failureCooldownSeconds:", "  # RTP 最终失败或取消后的冷却秒数，0 表示不冷却"},
            {"  maxAttempts:", "  # 每个请求最多尝试的随机位置数量（1-100）"},
            {"  timeoutSeconds:", "  # 寻找安全位置的总超时时间（秒，1-120）"},
            {"  loadTimeoutSeconds:", "  # 单次区块加载的超时时间（秒，1-30）"},
            {"  invulnerabilityTicks:", "  # 传送后的普通伤害保护时长（tick，0-1200），0 表示关闭"},
            {"  biomeBlacklist:", "  # 禁用群系：支持完整 ID 和以 # 开头的标签（标签需加引号）"},
            {"  floorBlacklist:", "  # 危险方块：支持完整 ID 和标签；液体、树叶、基岩始终禁止"},
            {"  dimensions:", "  # 按维度 ID 覆盖 mode(auto/surface/interior)、centerX/Z、minRange/maxRange 及黑名单"},
        };
        StringBuilder sb = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            for (String[] rule : rules) {
                if (line.startsWith(rule[0])) { sb.append(rule[1]).append("\n"); break; }
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
                    || !hasKeys(data, "back",
                            "enabled", "deleteAfterTeleport")
                    || !hasKeys(data, "home",
                            "enabled", "playerMaximum", "deleteInvalid", "delay")
                    || !hasKeys(data, "tpa",
                            "enabled", "delay", "cancelOnMove",
                            "requestExpireReminder")
                    || !hasKeys(data, "warp",
                            "enabled", "deleteInvalid")
                    || !hasKeys(data, "spawn",
                            "enabled", "world_id")
                    || !hasKeys(data, "rtp",
                            "enabled", "minRange", "maxRange", "cooldownEnabled", "cooldownSeconds", "maxConcurrentLoads",
                            "failureCooldownSeconds", "maxAttempts", "timeoutSeconds", "loadTimeoutSeconds",
                            "invulnerabilityTicks", "biomeBlacklist", "floorBlacklist", "dimensions");
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
        if (!text.equals(original)) Files.writeString(CONFIG_FILE, text, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasKeys(
            Map<String, Object> root, String section, String... keys) {
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
    private static ConfigClass fromMap(Map<String, Object> data) {
        ConfigClass cfg = new ConfigClass();
        if (data.containsKey("language")) cfg.language = (String) data.get("language");
        if (data.containsKey("back")) {
            Map<String, Object> b = (Map<String, Object>) data.get("back");
            if (b.containsKey("enabled"))             cfg.back.enabled            = (boolean) b.get("enabled");
            if (b.containsKey("deleteAfterTeleport")) cfg.back.deleteAfterTeleport = (boolean) b.get("deleteAfterTeleport");
        }
        if (data.containsKey("home")) {
            Map<String, Object> h = (Map<String, Object>) data.get("home");
            if (h.containsKey("enabled"))       cfg.home.enabled       = (boolean) h.get("enabled");
            if (h.containsKey("playerMaximum")) cfg.home.playerMaximum = (int)     h.get("playerMaximum");
            if (h.containsKey("deleteInvalid")) cfg.home.deleteInvalid = (boolean) h.get("deleteInvalid");
            if (h.containsKey("delay"))         cfg.home.delay         = (int)     h.get("delay");
        }
        if (data.containsKey("tpa")) {
            Map<String, Object> t = (Map<String, Object>) data.get("tpa");
            if (t.containsKey("enabled"))               cfg.tpa.enabled               = (boolean) t.get("enabled");
            if (t.containsKey("delay"))                    cfg.tpa.delay                    = (int)     t.get("delay");
            if (t.containsKey("cancelOnMove"))             cfg.tpa.cancelOnMove             = (boolean) t.get("cancelOnMove");
            if (t.containsKey("requestExpireReminder"))   cfg.tpa.requestExpireReminder   = (int)     t.get("requestExpireReminder");
        }
        if (data.containsKey("warp")) {
            Map<String, Object> w = (Map<String, Object>) data.get("warp");
            if (w.containsKey("enabled"))       cfg.warp.enabled       = (boolean) w.get("enabled");
            if (w.containsKey("deleteInvalid")) cfg.warp.deleteInvalid = (boolean) w.get("deleteInvalid");
        }
        if (data.containsKey("spawn")) {
            Map<String, Object> s = (Map<String, Object>) data.get("spawn");
            if (s.containsKey("enabled"))  cfg.spawn.enabled  = (boolean) s.get("enabled");
            if (s.containsKey("world_id")) cfg.spawn.world_id = (String)  s.get("world_id");
        }
        if (data.containsKey("rtp")) {
            Map<String, Object> r = (Map<String, Object>) data.get("rtp");
            if (r.containsKey("enabled"))  cfg.rtp.enabled  = (boolean) r.get("enabled");
            if (r.containsKey("minRange")) cfg.rtp.minRange = (int)     r.get("minRange");
            if (r.containsKey("maxRange")) cfg.rtp.maxRange = (int)     r.get("maxRange");
            if (r.containsKey("cooldownEnabled")) cfg.rtp.cooldownEnabled = (boolean) r.get("cooldownEnabled");
            if (r.containsKey("cooldownSeconds")) cfg.rtp.cooldownSeconds = Math.max(0, ((Number) r.get("cooldownSeconds")).intValue());
            cfg.rtp.maxConcurrentLoads = number(r, "maxConcurrentLoads", 10, 1, 10);
            cfg.rtp.failureCooldownSeconds = number(r, "failureCooldownSeconds", 30, 0, 86400);
            cfg.rtp.maxAttempts = number(r, "maxAttempts", 10, 1, 100);
            cfg.rtp.timeoutSeconds = number(r, "timeoutSeconds", 15, 1, 120);
            cfg.rtp.loadTimeoutSeconds = number(r, "loadTimeoutSeconds", 5, 1, 30);
            cfg.rtp.invulnerabilityTicks = number(r, "invulnerabilityTicks", 60, 0, 1200);
            cfg.rtp.biomeBlacklist = strings(r.get("biomeBlacklist"), cfg.rtp.biomeBlacklist);
            cfg.rtp.floorBlacklist = strings(r.get("floorBlacklist"), cfg.rtp.floorBlacklist);
            if (r.get("dimensions") instanceof Map<?, ?> dims) dims.forEach((id, raw) -> {
                if (!(raw instanceof Map<?, ?> values)) return;
                ConfigClass.Rtp.Dimension d = new ConfigClass.Rtp.Dimension();
                if (values.get("mode") instanceof String mode && List.of("auto", "surface", "interior").contains(mode)) d.mode = mode;
                if (values.containsKey("centerX")) d.centerX = number(values, "centerX", 0, -29999984, 29999984);
                if (values.containsKey("centerZ")) d.centerZ = number(values, "centerZ", 0, -29999984, 29999984);
                if (values.containsKey("minRange")) d.minRange = number(values, "minRange", 0, 0, 29999984);
                if (values.containsKey("maxRange")) d.maxRange = number(values, "maxRange", 2000, 0, 29999984);
                if (values.containsKey("biomeBlacklist")) d.biomeBlacklist = strings(values.get("biomeBlacklist"), cfg.rtp.biomeBlacklist);
                if (values.containsKey("floorBlacklist")) d.floorBlacklist = strings(values.get("floorBlacklist"), cfg.rtp.floorBlacklist);
                cfg.rtp.dimensions.put(String.valueOf(id), d);
            });
        }
        if (cfg.language == null || cfg.language.isBlank()) {
            Constants.LOGGER.warn("language cannot be empty; using zh_cn.");
            cfg.language = "zh_cn";
        }
        if (cfg.spawn.world_id == null || cfg.spawn.world_id.isBlank()) {
            Constants.LOGGER.warn(
                    "spawn.world_id cannot be empty; using minecraft:overworld.");
            cfg.spawn.world_id = "minecraft:overworld";
        }
        if (cfg.home.playerMaximum < 0) {
            Constants.LOGGER.warn("home.playerMaximum cannot be negative; using 0.");
            cfg.home.playerMaximum = 0;
        }
        if (cfg.home.delay < 0) {
            Constants.LOGGER.warn("home.delay cannot be negative; using 0.");
            cfg.home.delay = 0;
        }
        if (cfg.tpa.delay < 0) {
            Constants.LOGGER.warn("tpa.delay cannot be negative; using 0.");
            cfg.tpa.delay = 0;
        }
        if (cfg.tpa.requestExpireReminder < 0) {
            Constants.LOGGER.warn(
                    "tpa.requestExpireReminder cannot be negative; using 0.");
            cfg.tpa.requestExpireReminder = 0;
        } else if (cfg.tpa.requestExpireReminder > 120) {
            Constants.LOGGER.warn(
                    "tpa.requestExpireReminder cannot exceed the 120-second request lifetime; using 120.");
            cfg.tpa.requestExpireReminder = 120;
        }

        final int worldBorderLimit = 29_999_984;
        if (cfg.rtp.minRange < 0) {
            Constants.LOGGER.warn("rtp.minRange cannot be negative; using 0.");
            cfg.rtp.minRange = 0;
        }
        if (cfg.rtp.minRange > worldBorderLimit) {
            Constants.LOGGER.warn(
                    "rtp.minRange exceeds the world border; using {}.",
                    worldBorderLimit);
            cfg.rtp.minRange = worldBorderLimit;
        }
        if (cfg.rtp.maxRange < cfg.rtp.minRange) {
            Constants.LOGGER.warn(
                    "rtp.maxRange cannot be less than rtp.minRange; using {}.",
                    cfg.rtp.minRange);
            cfg.rtp.maxRange = cfg.rtp.minRange;
        }
        if (cfg.rtp.maxRange > worldBorderLimit) {
            Constants.LOGGER.warn(
                    "rtp.maxRange exceeds the world border; using {}.",
                    worldBorderLimit);
            cfg.rtp.maxRange = worldBorderLimit;
        }
        return cfg;
    }

    private static int number(Map<?, ?> map, String key, int fallback, int min, int max) {
        Object value = map.get(key);
        return value instanceof Number n ? (int) Math.max(min, Math.min(max, n.longValue())) : fallback;
    }
    private static List<String> strings(Object raw, List<String> fallback) {
        if (!(raw instanceof List<?> list)) return new ArrayList<>(fallback);
        List<String> result = new ArrayList<>();
        for (Object item : list) if (item instanceof String text && !text.isBlank()) result.add(text);
        return result;
    }

    public static class ConfigClass {
        public String language = "zh_cn";
        public Back  back  = new Back();
        public Home  home  = new Home();
        public Tpa   tpa   = new Tpa();
        public Warp  warp  = new Warp();
        public Spawn spawn = new Spawn();
        public Rtp   rtp   = new Rtp();

        public static class Back {
            public boolean enabled = true;
            public boolean deleteAfterTeleport = false;
            public boolean isEnabled()             { return enabled; }
            public boolean isDeleteAfterTeleport() { return deleteAfterTeleport; }
        }
        public static class Home {
            public boolean enabled = true;
            public int playerMaximum = 20;
            public boolean deleteInvalid = false;
            public int delay = 0;
            public boolean isEnabled()        { return enabled; }
            public int     getPlayerMaximum() { return playerMaximum; }
            public boolean isDeleteInvalid()  { return deleteInvalid; }
            public int     getDelay()         { return delay; }
        }
        public static class Tpa {
            public boolean enabled = true;
            public int delay = 3;
            public boolean cancelOnMove = true;
            public int requestExpireReminder = 30;
            public boolean isEnabled()               { return enabled; }
            public int     getDelay()                { return delay; }
            public boolean isCancelOnMove()          { return cancelOnMove; }
            public int     getRequestExpireReminder(){ return requestExpireReminder; }
        }
        public static class Warp {
            public boolean enabled = true;
            public boolean deleteInvalid = false;
            public boolean isEnabled()       { return enabled; }
            public boolean isDeleteInvalid() { return deleteInvalid; }
        }
        public static class Spawn {
            public boolean enabled = true;
            public String world_id = "minecraft:overworld";
            public boolean isEnabled()   { return enabled; }
            public String  getWorld_id() { return world_id; }
        }
        public static class Rtp {
            public boolean enabled = true;
            public int minRange = 1000;
            public int maxRange = 2000;
            public boolean cooldownEnabled = true;
            public int cooldownSeconds = 30;
            public int maxConcurrentLoads = 10;
            public int failureCooldownSeconds = 30;
            public int maxAttempts = 10;
            public int timeoutSeconds = 15;
            public int loadTimeoutSeconds = 5;
            public int invulnerabilityTicks = 60;
            public List<String> biomeBlacklist = new ArrayList<>(List.of("#minecraft:is_ocean", "#minecraft:is_river"));
            public List<String> floorBlacklist = new ArrayList<>(List.of("minecraft:lava", "minecraft:water", "minecraft:magma_block",
                    "minecraft:powder_snow", "#minecraft:leaves", "minecraft:cactus", "minecraft:fire", "minecraft:soul_fire",
                    "minecraft:sweet_berry_bush", "minecraft:cobweb", "minecraft:campfire", "minecraft:soul_campfire"));
            public Map<String, Dimension> dimensions = new LinkedHashMap<>();
            public static class Dimension {
                public String mode = "auto";
                public Integer centerX, centerZ, minRange, maxRange;
                public List<String> biomeBlacklist, floorBlacklist;
            }
            public boolean isEnabled()   { return enabled; }
            public int     getMinRange() { return minRange; }
            public int     getMaxRange() { return maxRange; }
        }
    }
}


