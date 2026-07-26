package cn.qaiu.parser;

import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * GHSA-997r-7xx2-p9x6 regression: Cloudreve generic parser must reject
 * hosts that resolve to loopback / private / link-local / metadata ranges
 * before any outbound request.
 */
public class AssertPublicHostTest {

    @Test
    public void rejectsLoopbackAndPrivateHosts() throws Exception {
        String[] blocked = {
                "http://127.0.0.1.nip.io/s/poc",
                "http://localhost/s/poc",
                "http://10.0.0.1/s/poc",
                "http://192.168.1.1/s/poc",
                "http://172.16.0.1/s/poc",
                "http://169.254.169.254/s/poc",
                "http://[::1]/s/poc"
        };
        for (String raw : blocked) {
            try {
                PanBase.assertPublicHost(new URL(raw));
                fail("expected block for " + raw);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("不允许访问")
                        || expected.getMessage().contains("无法解析"));
            }
        }
    }

    @Test
    public void allowsPublicHost() throws Exception {
        PanBase.assertPublicHost(new URL("https://example.com/s/demo"));
    }
}
