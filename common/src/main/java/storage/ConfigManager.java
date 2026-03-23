package tpa;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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
        root.put("rtp", rtp);

        Files.createDirectories(CONFIG_FILE.getParent());
        StringWriter sw = new StringWriter();
        yaml.dump(root, sw);
        String raw = insertComments(sw.toString());
        try (Writer writer = new FileWriter(CONFIG_FILE.toFile())) {
            writer.write("# TPA 插件配置文件\n# 修改后需重启服务器生效\n\n");
            writer.write(raw);
        }
    }

    private static String insertComments(String yaml) {
        String[][] rules = {
            {"language:",        "# 语言设置，可选值: zh_cn, en_us"},
            {"back:",            "# /back 命令配置"},
            {"home:",            "# /home 命令配置"},
            {"tpa:",             "# /tpa 命令配置"},
            {"warp:",            "# /warp 命令配置"},
            {"spawn:",           "# /spawn 命令配置"},
            {"rtp:",             "# /rtp 命令配置"},
            {"  enabled:",             "  # 是否启用该命令"},
            {"  deleteAfterTeleport:", "  # 传送后是否删除死亡位置记录"},
            {"  playerMaximum:",       "  # 每位玩家最多可以设置的家的数量"},
            {"  deleteInvalid:",       "  # 是否自动删除无效的位置（世界不存在时）"},
            {"  delay:",               "  # 传送等待时间（秒），0 表示立即传送"},
            {"  cancelOnMove:",        "  # 传送等待期间移动是否取消传送"},
            {"  requestExpireReminder:", "  # TPA 请求过期前的提醒时间（秒），0 表示不提醒"},
            {"  world_id:",            "  # 出生点所在世界的 ID，默认为主世界"},
            {"  minRange:",            "  # 随机传送最小范围（方块）"},
            {"  maxRange:",            "  # 随机传送最大范围（方块）"},
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
            return !data.containsKey("rtp") || !data.containsKey("back") ||
                   !data.containsKey("home") || !data.containsKey("tpa") ||
                   !data.containsKey("warp") || !data.containsKey("spawn");
        }
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
        }
        return cfg;
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
            public boolean isEnabled()   { return enabled; }
            public int     getMinRange() { return minRange; }
            public int     getMaxRange() { return maxRange; }
        }
    }
}
