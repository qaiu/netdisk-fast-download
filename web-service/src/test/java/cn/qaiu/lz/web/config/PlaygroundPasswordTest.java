package cn.qaiu.lz.web.config;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlaygroundPasswordTest {
    @Test
    public void validatesPrivateModeAfterTheEnableOverride() {
        String key = "NFD_PLAYGROUND_ENABLED";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            JsonObject playground = new JsonObject().put("enabled", false);
            JsonObject config = new JsonObject().put("playground", playground);
            for (String password : new String[]{null, "", "  ", "nfd_playground_2024"}) {
                playground.put("password", password);
                PlaygroundConfig.loadFromJson(config);
                assertFalse(PlaygroundConfig.getInstance().isEnabled());
            }
            playground.put("password", "instance-specific-test-value");
            PlaygroundConfig.loadFromJson(config);
            assertTrue(PlaygroundConfig.getInstance().isEnabled());
            assertEquals(playground.getString("password"), PlaygroundConfig.getInstance().getPassword());
            String token = PlaygroundConfig.getInstance().generateToken();

            PlaygroundConfig.loadFromJson(new JsonObject());
            assertFalse(PlaygroundConfig.getInstance().isEnabled());
            assertFalse(PlaygroundConfig.getInstance().validateToken(token));

            playground.put("public", true).remove("password");
            PlaygroundConfig.loadFromJson(config);
            assertTrue(PlaygroundConfig.getInstance().isEnabled());
            System.setProperty(key, "false");
            PlaygroundConfig.loadFromJson(config);
            assertFalse(PlaygroundConfig.getInstance().isEnabled());
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
            PlaygroundConfig.loadFromJson(new JsonObject());
        }
    }
}
