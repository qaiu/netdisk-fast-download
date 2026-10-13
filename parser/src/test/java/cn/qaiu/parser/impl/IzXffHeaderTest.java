package cn.qaiu.parser.impl;

import io.vertx.core.MultiMap;
import org.junit.Test;

import static org.junit.Assert.*;

public class IzXffHeaderTest {
    @Test
    public void offByDefault() {
        assertNull(IzXffHeader.resolve(null));
        assertNull(IzXffHeader.resolve(""));
        assertNull(IzXffHeader.resolve("OFF"));
    }

    @Test
    public void literalAndRandom() {
        assertEquals("113.96.181.18", IzXffHeader.resolve("113.96.181.18"));
        String ip = IzXffHeader.resolve("random");
        assertTrue(ip, ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"));
    }

    @Test
    public void applyRemovesWhenOff() {
        MultiMap h = MultiMap.caseInsensitiveMultiMap().set("X-Forwarded-For", "1.1.1.1");
        System.clearProperty(IzXffHeader.PROP);
        if (System.getenv(IzXffHeader.ENV) == null) {
            IzXffHeader.apply(h);
            assertFalse(h.contains("X-Forwarded-For"));
        }
        System.setProperty(IzXffHeader.PROP, "8.8.8.8");
        IzXffHeader.apply(h);
        assertEquals("8.8.8.8", h.get("X-Forwarded-For"));
        System.clearProperty(IzXffHeader.PROP);
    }
}
