package cn.qaiu.vx.core.verticle;

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class HttpProxyBase64Test {
    @Test
    public void matchesJdkBasicDecoder() {
        for (String value : new String[]{"", "Zg", "Zg==", "Zm8=", "dXNlcjpwYXNz", "+/8=", "ABCD",
                "AB==", "Z", "Zg=", "Zm8==", "Zg==suffix", "Zg==\n", "-_8=", "AA\u2603", "===="}) {
            byte[] expected;
            try {
                expected = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException expectedFailure) {
                try {
                    HttpProxyVerticle.decodeProxyAuthorization(value);
                } catch (IllegalArgumentException actualFailure) {
                    continue;
                }
                fail("Accepted invalid proxy credentials: " + value);
                return;
            }
            assertArrayEquals(expected, HttpProxyVerticle.decodeProxyAuthorization(value));
        }
    }
}
