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
    }

    @Test void acceptsDisabledAndCustomCooldown() throws Exception {
        var config = parse(Map.of("cooldownEnabled", false, "cooldownSeconds", 7));
        assertFalse(config.rtp.cooldownEnabled);
        assertEquals(7, config.rtp.cooldownSeconds);
    }

    @Test void negativeCooldownBecomesZero() throws Exception {
        assertEquals(0, parse(Map.of("cooldownSeconds", -5)).rtp.cooldownSeconds);
    }
}
