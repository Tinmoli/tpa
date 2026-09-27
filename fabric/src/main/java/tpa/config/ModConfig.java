package tpa.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    public String language = "zh_cn";
    public Back back = new Back();
    public Home home = new Home();
    public Tpa tpa = new Tpa();
    public Warp warp = new Warp();
    public Spawn spawn = new Spawn();
    public Rtp rtp = new Rtp();

    public static class Back {
        public boolean enabled = true;
        public boolean deleteAfterTeleport = false;

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isDeleteAfterTeleport() {
            return deleteAfterTeleport;
        }
    }

    public static class Home {
        public boolean enabled = true;
        public int playerMaximum = 20;
        public boolean deleteInvalid = false;
        public int delay = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public int getPlayerMaximum() {
            return playerMaximum;
        }

        public boolean isDeleteInvalid() {
            return deleteInvalid;
        }

        public int getDelay() {
            return delay;
        }
    }

    public static class Tpa {
        public boolean enabled = true;
        public int delay = 3;
        public boolean cancelOnMove = true;
        public int requestExpireReminder = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public int getDelay() {
            return delay;
        }

        public boolean isCancelOnMove() {
            return cancelOnMove;
        }

        public int getRequestExpireReminder() {
            return requestExpireReminder;
        }
    }

    public static class Warp {
        public boolean enabled = true;
        public boolean deleteInvalid = false;

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isDeleteInvalid() {
            return deleteInvalid;
        }
    }

    public static class Spawn {
        public boolean enabled = true;
        public String world_id = "minecraft:overworld";

        public boolean isEnabled() {
            return enabled;
        }

        public String getWorldId() {
            return world_id;
        }
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
        public List<String> biomeBlacklist =
                new ArrayList<>(List.of("#minecraft:is_ocean", "#minecraft:is_river"));
        public List<String> floorBlacklist =
                new ArrayList<>(
                        List.of(
                                "minecraft:lava",
                                "minecraft:water",
                                "minecraft:magma_block",
                                "minecraft:powder_snow",
                                "#minecraft:leaves",
                                "minecraft:cactus",
                                "minecraft:fire",
                                "minecraft:soul_fire",
                                "minecraft:sweet_berry_bush",
                                "minecraft:cobweb",
                                "minecraft:campfire",
                                "minecraft:soul_campfire"));
        public Map<String, Dimension> dimensions = new LinkedHashMap<>();

        public static class Dimension {
            public String mode = "auto";
            public Integer centerX, centerZ, minRange, maxRange;
            public List<String> biomeBlacklist, floorBlacklist;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMinRange() {
            return minRange;
        }

        public int getMaxRange() {
            return maxRange;
        }
    }
}
