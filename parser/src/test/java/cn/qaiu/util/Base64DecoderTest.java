package cn.qaiu.util;

import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.function.Function;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Base64DecoderTest {
    @Test
    public void matchesJdkForStandardAndUrlSafeEncodings() {
        Random random = new Random(114938);
        for (int length = 0; length <= 512; length++) {
            assertRoundTrip(random, length);
        }
    }

    @Test
    public void decodesCompleteGroupsAndEveryTailAcrossLargeInputs() {
        Random random = new Random(20261120);
        for (int boundary : new int[]{1024, 8192, 65536}) {
            for (int offset = -2; offset <= 2; offset++) {
                assertRoundTrip(random, boundary + offset);
            }
        }
    }

    @Test
    public void matchesJdkForInvalidCharactersAtGroupAndTailBoundaries() {
        for (int length : new int[]{4, 6, 7, 8}) {
            String valid = "A".repeat(length);
            for (int i = 0; i < length; i++) {
                for (String invalid : new String[]{"!", "=", "\u0141", "\ud83d\ude00"}) {
                    String value = valid.substring(0, i) + invalid + valid.substring(i + 1);
                    assertCompatible(value, Base64.getDecoder(), Base64Decoder::decode);
                    assertCompatible(value, Base64.getUrlDecoder(), Base64Decoder::decodeUrl);
                }
            }
        }
    }

    @Test
    public void matchesJdkForPaddingAlphabetAndInvalidCharacters() {
        for (String value : new String[]{"", "A", "AA", "AB==", "ABC=", "ABCD", "AA=", "AAA==",
                "AA==AA", "AA==\n", " AA", "A A", "AA\u00e9", "AA\u2603", "+/8=", "-_8=", "+_8=", "===="}) {
            assertCompatible(value, Base64.getDecoder(), Base64Decoder::decode);
            assertCompatible(value, Base64.getUrlDecoder(), Base64Decoder::decodeUrl);
        }
    }

    @Test
    public void preservesLegacyDownloadLinkFormats() {
        String value = "网盘下载😀";
        String standard = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        String escaped = URLEncoder.encode(standard, StandardCharsets.UTF_8);
        assertEquals(value, CommonUtils.urlBase64Decode(CommonUtils.urlBase64Encode(value)));
        assertEquals(value, CommonUtils.urlBase64Decode(standard));
        assertEquals(value, CommonUtils.urlBase64Decode(escaped));
        assertEquals(value, CommonUtils.urlBase64Decode(URLEncoder.encode(escaped, StandardCharsets.UTF_8)));
        assertEquals("f", CommonUtils.urlBase64Decode("Zg="));
    }

    private static void assertRoundTrip(Random random, int length) {
        byte[] value = new byte[length];
        random.nextBytes(value);
        assertArrayEquals(value, Base64Decoder.decode(Base64.getEncoder().encodeToString(value)));
        assertArrayEquals(value, Base64Decoder.decode(Base64.getEncoder().withoutPadding().encodeToString(value)));
        assertArrayEquals(value, Base64Decoder.decodeUrl(Base64.getUrlEncoder().encodeToString(value)));
        assertArrayEquals(value, Base64Decoder.decodeUrl(Base64.getUrlEncoder().withoutPadding().encodeToString(value)));
    }

    private static void assertCompatible(String value, Base64.Decoder jdk, Function<String, byte[]> replacement) {
        byte[] expected;
        try {
            expected = jdk.decode(value);
        } catch (IllegalArgumentException expectedFailure) {
            try {
                replacement.apply(value);
            } catch (IllegalArgumentException actualFailure) {
                return;
            }
            fail("Accepted invalid Base64 input: " + value);
            return;
        }
        assertArrayEquals(expected, replacement.apply(value));
    }
}
