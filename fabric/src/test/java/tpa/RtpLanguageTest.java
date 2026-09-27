package tpa;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import static org.junit.jupiter.api.Assertions.*;

class RtpLanguageTest {
    @Test void bothLanguagesRenderRemainingSeconds() throws Exception {
        var method = tools.class.getDeclaredMethod("buildComponent", String.class, Pattern.class, MutableComponent[].class);
        method.setAccessible(true);
        for (String language : new String[]{"zh_cn", "en_us"}) {
            try (var input = getClass().getResourceAsStream("/assets/tpa/lang/" + language + ".json")) {
                assertNotNull(input);
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                String text = json.get("commands.teleport_commands.rtp.cooldown_left").getAsString();
                Component result = (Component) method.invoke(null, text, Pattern.compile("%(\\d+)%"),
                        new MutableComponent[]{Component.literal("17")});
                assertTrue(result.getString().contains("17"));
                assertFalse(result.getString().contains("%"));
            }
        }
    }
}
