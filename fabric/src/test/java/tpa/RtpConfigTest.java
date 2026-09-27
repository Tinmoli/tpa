package tpa;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RtpConfigTest {
    private ConfigManager.ConfigClass parse(Map<String, Object> rtp) throws Exception {
        Method method = ConfigManager.class.getDeclaredMethod("fromMap", Map.class);
        method.setAccessible(true);
        return (ConfigManager.ConfigClass) method.invoke(null, Map.of("rtp", rtp));
    }

    @Test void legacyConfigRetainsDefaultCooldown() throws Exception {
        var config = parse(Map.of("maxRange", 4321));
        assertEquals(4321, config.rtp.maxRange);
        assertTrue(config.rtp.cooldownEnabled);
        assertEquals(30, config.rtp.cooldownSeconds);
        assertEquals(10, config.rtp.maxConcurrentLoads);
    }

    @Test void acceptsDisabledAndCustomCooldown() throws Exception {
        var config = parse(Map.of("cooldownEnabled", false, "cooldownSeconds", 7));
        assertFalse(config.rtp.cooldownEnabled);
        assertEquals(7, config.rtp.cooldownSeconds);
    }

    @Test void negativeCooldownBecomesZero() throws Exception {
        assertEquals(0, parse(Map.of("cooldownSeconds", -5)).rtp.cooldownSeconds);
    }

    @Test void concurrencyAcceptsTenAndClampsInvalidLimits() throws Exception {
        assertEquals(10, parse(Map.of("maxConcurrentLoads", 10)).rtp.maxConcurrentLoads);
        assertEquals(10, parse(Map.of("maxConcurrentLoads", 100)).rtp.maxConcurrentLoads);
        assertEquals(1, parse(Map.of("maxConcurrentLoads", 0)).rtp.maxConcurrentLoads);
    }

    @Test void oldCooldownAndConcurrencyArePreserved() throws Exception {
        var c = parse(Map.of("cooldownSeconds", 17, "maxConcurrentLoads", 1)).rtp;
        assertEquals(17, c.cooldownSeconds); assertEquals(1, c.maxConcurrentLoads);
        assertEquals(30, c.failureCooldownSeconds); assertEquals(15, c.timeoutSeconds);
    }

    @Test void limitsAndDimensionOverridesAreParsed() throws Exception {
        var c = parse(Map.of("maxAttempts", 999, "timeoutSeconds", -1, "dimensions",
                Map.of("custom:moon", Map.of("mode", "surface", "centerX", 700, "minRange", 50,
                        "biomeBlacklist", java.util.List.of("custom:acid"))))).rtp;
        assertEquals(100, c.maxAttempts); assertEquals(1, c.timeoutSeconds);
        assertEquals(700, c.dimensions.get("custom:moon").centerX);
        assertEquals(java.util.List.of("custom:acid"), c.dimensions.get("custom:moon").biomeBlacklist);
    }

    @Test void oldFileUpgradeAddsCommentsAndPreservesValues(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var oldDir = tpa.CONFIG_DIR; var oldFile = ConfigManager.CONFIG_FILE; var oldConfig = ConfigManager.CONFIG;
        try {
            tpa.CONFIG_DIR = dir; ConfigManager.CONFIG_FILE = dir.resolve("config.yml");
            java.nio.file.Files.writeString(ConfigManager.CONFIG_FILE,
                    "rtp:\n  minRange: 500\n  maxRange: 1400\n  cooldownSeconds: 17\n  maxConcurrentLoads: 1\n");
            ConfigManager.ConfigLoader();
            String saved = java.nio.file.Files.readString(ConfigManager.CONFIG_FILE);
            assertEquals(17, ConfigManager.CONFIG.rtp.cooldownSeconds);
            assertTrue(saved.contains("failureCooldownSeconds: 30"));
            assertTrue(saved.contains("timeoutSeconds: 15"));
            assertTrue(saved.contains("# RTP 最终失败"));
            assertTrue(saved.contains("# 按维度 ID 覆盖"));
            java.nio.file.Files.writeString(ConfigManager.CONFIG_FILE, "# 我的配置备注\n" + saved.replace(
                    "# RTP 成功后的冷却时间（秒），0 表示不冷却",
                    "# RTP 成功后的冷却秒数，0 表示成功后不冷却；保留旧配置值"));
            ConfigManager.ConfigLoader();
            String refreshed = java.nio.file.Files.readString(ConfigManager.CONFIG_FILE);
            assertFalse(refreshed.contains("保留旧配置值"));
            assertTrue(refreshed.contains("# 我的配置备注"));
            assertEquals(17, ConfigManager.CONFIG.rtp.cooldownSeconds);
        } finally { tpa.CONFIG_DIR = oldDir; ConfigManager.CONFIG_FILE = oldFile; ConfigManager.CONFIG = oldConfig; }
    }
}
