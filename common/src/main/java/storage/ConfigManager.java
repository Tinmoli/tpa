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

        // Re-save to fill any missing keys
        ConfigSaver();
        Constants.LOGGER.info("Config loaded successfully!");
    }

    public static void ConfigSaver() throws Exception {
        StringBuilder sb = new StringBuilder();
        ConfigClass cfg = CONFIG;

        sb.append("# TPA 插件配置文件\n");
        sb.append("# 修改后需要使用 /tpa reload 命令重载，或重启服务器\n");
        sb.append("\n");

        sb.append("# 语言设置，可选值: zh_cn, en_us（或自定义语言文件名，不含扩展名）\n");
        sb.append("language: ").append(cfg.language).append("\n");
        sb.append("\n");

        sb.append("# /back 命令配置\n");
        sb.append("back:\n");
        sb.append("  # 是否启用 /back 命令\n");
        sb.append("  enabled: ").append(cfg.back.enabled).append("\n");
        sb.append("  # 传送后是否删除死亡位置记录\n");
        sb.append("  deleteAfterTeleport: ").append(cfg.back.deleteAfterTeleport).append("\n");
        sb.append("\n");

        sb.append("# /home 命令配置\n");
        sb.append("home:\n");
        sb.append("  # 是否启用 /home 命令\n");
        sb.append("  enabled: ").append(cfg.home.enabled).append("\n");
        sb.append("  # 每位玩家最多可以设置的家的数量\n");
        sb.append("  playerMaximum: ").append(cfg.home.playerMaximum).append("\n");
        sb.append("  # 是否自动删除无效的家（世界不存在时）\n");
        sb.append("  deleteInvalid: ").append(cfg.home.deleteInvalid).append("\n");
        sb.append("  # 传送等待时间（秒），0 表示立即传送\n");
        sb.append("  delay: ").append(cfg.home.delay).append("\n");
        sb.append("\n");

        sb.append("# /tpa 命令配置\n");
        sb.append("tpa:\n");
        sb.append("  # 是否启用 /tpa 命令\n");
        sb.append("  enabled: ").append(cfg.tpa.enabled).append("\n");
        sb.append("  # 传送等待时间（秒），0 表示立即传送\n");
        sb.append("  delay: ").append(cfg.tpa.delay).append("\n");
        sb.append("  # 传送等待期间移动是否取消传送\n");
        sb.append("  cancelOnMove: ").append(cfg.tpa.cancelOnMove).append("\n");
        sb.append("\n");

        sb.append("# /warp 命令配置\n");
        sb.append("warp:\n");
        sb.append("  # 是否启用 /warp 命令\n");
        sb.append("  enabled: ").append(cfg.warp.enabled).append("\n");
        sb.append("  # 是否自动删除无效的传送点\n");
        sb.append("  deleteInvalid: ").append(cfg.warp.deleteInvalid).append("\n");
        sb.append("\n");

        sb.append("# /spawn 命令配置\n");
        sb.append("spawn:\n");
        sb.append("  # 是否启用 /spawn 命令\n");
        sb.append("  enabled: ").append(cfg.spawn.enabled).append("\n");
        sb.append("  # 出生点所在世界的 ID，默认为主世界\n");
        sb.append("  world_id: ").append(cfg.spawn.world_id).append("\n");
        sb.append("\n");

        try (Writer writer = new FileWriter(CONFIG_FILE.toFile())) {
            writer.write(sb.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static ConfigClass fromMap(Map<String, Object> data) {
        ConfigClass cfg = new ConfigClass();

        if (data.containsKey("language")) cfg.language = (String) data.get("language");

        if (data.containsKey("back")) {
            Map<String, Object> back = (Map<String, Object>) data.get("back");
            if (back.containsKey("enabled"))            cfg.back.enabled           = (boolean) back.get("enabled");
            if (back.containsKey("deleteAfterTeleport")) cfg.back.deleteAfterTeleport = (boolean) back.get("deleteAfterTeleport");
        }
        if (data.containsKey("home")) {
            Map<String, Object> home = (Map<String, Object>) data.get("home");
            if (home.containsKey("enabled"))       cfg.home.enabled       = (boolean) home.get("enabled");
            if (home.containsKey("playerMaximum")) cfg.home.playerMaximum = (int) home.get("playerMaximum");
            if (home.containsKey("deleteInvalid")) cfg.home.deleteInvalid = (boolean) home.get("deleteInvalid");
            if (home.containsKey("delay"))         cfg.home.delay         = (int) home.get("delay");
        }
        if (data.containsKey("tpa")) {
            Map<String, Object> tpa = (Map<String, Object>) data.get("tpa");
            if (tpa.containsKey("enabled"))      cfg.tpa.enabled      = (boolean) tpa.get("enabled");
            if (tpa.containsKey("delay"))        cfg.tpa.delay        = (int)     tpa.get("delay");
            if (tpa.containsKey("cancelOnMove")) cfg.tpa.cancelOnMove = (boolean) tpa.get("cancelOnMove");
        }
        if (data.containsKey("warp")) {
            Map<String, Object> warp = (Map<String, Object>) data.get("warp");
            if (warp.containsKey("enabled"))       cfg.warp.enabled       = (boolean) warp.get("enabled");
            if (warp.containsKey("deleteInvalid")) cfg.warp.deleteInvalid = (boolean) warp.get("deleteInvalid");
        }
        if (data.containsKey("spawn")) {
            Map<String, Object> spawn = (Map<String, Object>) data.get("spawn");
            if (spawn.containsKey("enabled"))  cfg.spawn.enabled  = (boolean) spawn.get("enabled");
            if (spawn.containsKey("world_id")) cfg.spawn.world_id = (String)  spawn.get("world_id");
        }
        return cfg;
    }

    public static class ConfigClass {
        public String   language = "zh_cn";
        public Back     back  = new Back();
        public Home     home  = new Home();
        public Tpa      tpa   = new Tpa();
        public Warp     warp  = new Warp();
        public Spawn    spawn = new Spawn();

        public static class Back {
            public boolean enabled            = true;
            public boolean deleteAfterTeleport = false;

            public boolean isEnabled()             { return enabled; }
            public boolean isDeleteAfterTeleport() { return deleteAfterTeleport; }
        }

        public static class Home {
            public boolean enabled       = true;
            public int     playerMaximum = 20;
            public boolean deleteInvalid = false;
            public int     delay        = 0;

            public boolean isEnabled()       { return enabled; }
            public int     getPlayerMaximum(){ return playerMaximum; }
            public boolean isDeleteInvalid() { return deleteInvalid; }
            public int     getDelay()        { return delay; }
        }

        public static class Tpa {
            public boolean enabled      = true;
            public int     delay        = 3;
            public boolean cancelOnMove = true;

            public boolean isEnabled()      { return enabled; }
            public int     getDelay()       { return delay; }
            public boolean isCancelOnMove() { return cancelOnMove; }
        }

        public static class Warp {
            public boolean enabled       = true;
            public boolean deleteInvalid = false;

            public boolean isEnabled()       { return enabled; }
            public boolean isDeleteInvalid() { return deleteInvalid; }
        }

        public static class Spawn {
            public boolean enabled  = true;
            public String  world_id = "minecraft:overworld";

            public boolean isEnabled()    { return enabled; }
            public String  getWorld_id()  { return world_id; }
        }

    }
}
