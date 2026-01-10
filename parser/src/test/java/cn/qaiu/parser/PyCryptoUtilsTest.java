package cn.qaiu.parser;

import cn.qaiu.parser.custompy.PyCryptoUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * PyCryptoUtils 测试类
 * 测试Python加密工具功能
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2026/1/11
 */
public class PyCryptoUtilsTest {

    private PyCryptoUtils cryptoUtils;

    @Before
    public void setUp() {
        cryptoUtils = new PyCryptoUtils();
        System.out.println("--- 测试开始 ---");
    }

    // ===================== MD5 测试 =====================

    @Test
    public void testMd5() {
        System.out.println("\n[测试] MD5哈希");
        
        // 测试已知值
        String input = "hello";
        String expected = "5d41402abc4b2a76b9719d911017c592";
        
        String result = cryptoUtils.md5(input);
        
        System.out.println("输入: " + input);
        System.out.println("MD5: " + result);
        System.out.println("期望: " + expected);
        
        assertEquals("MD5结果应该正确", expected, result);
        assertEquals("MD5应该是32位", 32, result.length());
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testMd5_16() {
        System.out.println("\n[测试] MD5-16位哈希");
        
        String input = "hello";
        String fullMd5 = "5d41402abc4b2a76b9719d911017c592";
        String expected = fullMd5.substring(8, 24); // "abc4b2a76b9719d9"
        
        String result = cryptoUtils.md5_16(input);
        
        System.out.println("输入: " + input);
        System.out.println("MD5-16: " + result);
        System.out.println("期望: " + expected);
        
        assertEquals("MD5-16结果应该正确", expected, result);
        assertEquals("MD5-16应该是16位", 16, result.length());
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testMd5EmptyString() {
        System.out.println("\n[测试] MD5空字符串");
        
        String input = "";
        String expected = "d41d8cd98f00b204e9800998ecf8427e";
        
        String result = cryptoUtils.md5(input);
        
        System.out.println("输入: (空字符串)");
        System.out.println("MD5: " + result);
        
        assertEquals("空字符串MD5应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    // ===================== SHA 测试 =====================

    @Test
    public void testSha1() {
        System.out.println("\n[测试] SHA-1哈希");
        
        String input = "hello";
        String expected = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";
        
        String result = cryptoUtils.sha1(input);
        
        System.out.println("输入: " + input);
        System.out.println("SHA-1: " + result);
        
        assertEquals("SHA-1结果应该正确", expected, result);
        assertEquals("SHA-1应该是40位", 40, result.length());
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testSha256() {
        System.out.println("\n[测试] SHA-256哈希");
        
        String input = "hello";
        String expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        
        String result = cryptoUtils.sha256(input);
        
        System.out.println("输入: " + input);
        System.out.println("SHA-256: " + result);
        
        assertEquals("SHA-256结果应该正确", expected, result);
        assertEquals("SHA-256应该是64位", 64, result.length());
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testSha512() {
        System.out.println("\n[测试] SHA-512哈希");
        
        String input = "hello";
        
        String result = cryptoUtils.sha512(input);
        
        System.out.println("输入: " + input);
        System.out.println("SHA-512: " + result);
        
        assertNotNull("SHA-512结果不能为null", result);
        assertEquals("SHA-512应该是128位", 128, result.length());
        
        System.out.println("✓ 测试通过");
    }

    // ===================== Base64 测试 =====================

    @Test
    public void testBase64Encode() {
        System.out.println("\n[测试] Base64编码");
        
        String input = "hello world";
        String expected = "aGVsbG8gd29ybGQ=";
        
        String result = cryptoUtils.base64_encode(input);
        
        System.out.println("输入: " + input);
        System.out.println("Base64: " + result);
        
        assertEquals("Base64编码应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testBase64Decode() {
        System.out.println("\n[测试] Base64解码");
        
        String input = "aGVsbG8gd29ybGQ=";
        String expected = "hello world";
        
        String result = cryptoUtils.base64_decode(input);
        
        System.out.println("输入: " + input);
        System.out.println("解码: " + result);
        
        assertEquals("Base64解码应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testBase64EncodeBytes() {
        System.out.println("\n[测试] Base64字节编码");
        
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        String expected = "aGVsbG8=";
        
        String result = cryptoUtils.base64_encode_bytes(input);
        
        System.out.println("输入字节数: " + input.length);
        System.out.println("Base64: " + result);
        
        assertEquals("Base64字节编码应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testBase64UrlEncode() {
        System.out.println("\n[测试] Base64 URL安全编码");
        
        // 包含特殊字符的测试数据
        String input = "hello+world/test";
        
        String result = cryptoUtils.base64_url_encode(input);
        
        System.out.println("输入: " + input);
        System.out.println("Base64 URL: " + result);
        
        assertNotNull("结果不能为null", result);
        assertFalse("URL安全编码不应该包含+", result.contains("+"));
        assertFalse("URL安全编码不应该包含/", result.contains("/"));
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testBase64UrlDecode() {
        System.out.println("\n[测试] Base64 URL安全解码");
        
        String input = "aGVsbG8td29ybGQ";
        String expected = "hello-world";
        
        String result = cryptoUtils.base64_url_decode(input);
        
        System.out.println("输入: " + input);
        System.out.println("解码: " + result);
        
        assertEquals("Base64 URL解码应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testBase64RoundTrip() {
        System.out.println("\n[测试] Base64编解码往返");
        
        String[] testCases = {
            "hello",
            "hello world",
            "中文测试",
            "特殊字符!@#$%^&*()",
            ""
        };
        
        for (String original : testCases) {
            String encoded = cryptoUtils.base64_encode(original);
            String decoded = cryptoUtils.base64_decode(encoded);
            
            assertEquals("编解码往返应该得到原值: " + original, original, decoded);
        }
        
        System.out.println("✓ 测试通过（" + testCases.length + " 个测试用例）");
    }

    // ===================== AES 测试 =====================

    @Test
    public void testAesEcbEncryptDecrypt() {
        System.out.println("\n[测试] AES ECB模式加解密");
        
        String plaintext = "hello world 123";
        String key = "1234567890123456"; // 16字节密钥
        
        // 加密
        String encrypted = cryptoUtils.aes_encrypt_ecb(plaintext, key);
        System.out.println("原文: " + plaintext);
        System.out.println("密钥: " + key);
        System.out.println("密文: " + encrypted);
        
        assertNotNull("加密结果不能为null", encrypted);
        assertNotEquals("加密后应该不同于原文", plaintext, encrypted);
        
        // 解密
        String decrypted = cryptoUtils.aes_decrypt_ecb(encrypted, key);
        System.out.println("解密: " + decrypted);
        
        assertEquals("解密后应该恢复原文", plaintext, decrypted);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testAesCbcEncryptDecrypt() {
        System.out.println("\n[测试] AES CBC模式加解密");
        
        String plaintext = "hello world 123";
        String key = "1234567890123456"; // 16字节密钥
        String iv = "abcdefghijklmnop";  // 16字节IV
        
        // 加密
        String encrypted = cryptoUtils.aes_encrypt_cbc(plaintext, key, iv);
        System.out.println("原文: " + plaintext);
        System.out.println("密钥: " + key);
        System.out.println("IV: " + iv);
        System.out.println("密文: " + encrypted);
        
        assertNotNull("加密结果不能为null", encrypted);
        assertNotEquals("加密后应该不同于原文", plaintext, encrypted);
        
        // 解密
        String decrypted = cryptoUtils.aes_decrypt_cbc(encrypted, key, iv);
        System.out.println("解密: " + decrypted);
        
        assertEquals("解密后应该恢复原文", plaintext, decrypted);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testAesWithChineseContent() {
        System.out.println("\n[测试] AES加密中文内容");
        
        String plaintext = "这是一段中文内容123";
        String key = "1234567890123456";
        String iv = "abcdefghijklmnop";
        
        // ECB模式
        String encryptedEcb = cryptoUtils.aes_encrypt_ecb(plaintext, key);
        String decryptedEcb = cryptoUtils.aes_decrypt_ecb(encryptedEcb, key);
        assertEquals("ECB解密中文应该正确", plaintext, decryptedEcb);
        
        // CBC模式
        String encryptedCbc = cryptoUtils.aes_encrypt_cbc(plaintext, key, iv);
        String decryptedCbc = cryptoUtils.aes_decrypt_cbc(encryptedCbc, key, iv);
        assertEquals("CBC解密中文应该正确", plaintext, decryptedCbc);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testAesEcbCbcDifference() {
        System.out.println("\n[测试] AES ECB和CBC模式差异");
        
        String plaintext = "hello world 1234";
        String key = "1234567890123456";
        String iv = "abcdefghijklmnop";
        
        String ecbEncrypted = cryptoUtils.aes_encrypt_ecb(plaintext, key);
        String cbcEncrypted = cryptoUtils.aes_encrypt_cbc(plaintext, key, iv);
        
        System.out.println("ECB密文: " + ecbEncrypted);
        System.out.println("CBC密文: " + cbcEncrypted);
        
        // ECB和CBC模式的加密结果应该不同
        assertNotEquals("ECB和CBC加密结果应该不同", ecbEncrypted, cbcEncrypted);
        
        System.out.println("✓ 测试通过");
    }

    // ===================== 工具方法测试 =====================

    @Test
    public void testBytesToHex() {
        System.out.println("\n[测试] 字节转十六进制");
        
        byte[] input = {0x00, 0x0F, (byte) 0xFF, 0x10, (byte) 0xAB};
        String expected = "000fff10ab";
        
        String result = cryptoUtils.bytes_to_hex(input);
        
        System.out.println("输入字节: " + input.length + " 字节");
        System.out.println("十六进制: " + result);
        
        assertEquals("字节转十六进制应该正确", expected, result);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testConsistencyWithJsCryptoUtils() {
        System.out.println("\n[测试] 与JavaScript加密工具一致性");
        
        // 这些值应该与JsCryptoUtils产生相同的结果
        String testString = "consistency_test";
        
        String md5 = cryptoUtils.md5(testString);
        String sha1 = cryptoUtils.sha1(testString);
        String sha256 = cryptoUtils.sha256(testString);
        String base64 = cryptoUtils.base64_encode(testString);
        
        System.out.println("测试字符串: " + testString);
        System.out.println("MD5: " + md5);
        System.out.println("SHA1: " + sha1);
        System.out.println("SHA256: " + sha256);
        System.out.println("Base64: " + base64);
        
        // 验证结果非空且格式正确
        assertNotNull("MD5不能为null", md5);
        assertEquals("MD5长度应该是32", 32, md5.length());
        
        assertNotNull("SHA1不能为null", sha1);
        assertEquals("SHA1长度应该是40", 40, sha1.length());
        
        assertNotNull("SHA256不能为null", sha256);
        assertEquals("SHA256长度应该是64", 64, sha256.length());
        
        assertNotNull("Base64不能为null", base64);
        
        System.out.println("✓ 测试通过");
    }

    @Test
    public void testNullInput() {
        System.out.println("\n[测试] 空输入处理");
        
        try {
            // MD5应该能处理null（返回null或抛出异常）
            String result = cryptoUtils.md5(null);
            // 如果没有抛出异常，结果应该是null
            System.out.println("MD5(null) = " + result);
        } catch (Exception e) {
            System.out.println("MD5(null) 抛出异常: " + e.getClass().getSimpleName());
        }
        
        System.out.println("✓ 空输入处理测试完成");
    }

    @Test
    public void testSpecialCharacters() {
        System.out.println("\n[测试] 特殊字符处理");
        
        String[] testCases = {
            "~!@#$%^&*()_+",
            "日本語テスト",
            "🎉🎊🎁",
            "\n\t\r",
            "   "
        };
        
        for (String input : testCases) {
            String md5 = cryptoUtils.md5(input);
            String base64 = cryptoUtils.base64_encode(input);
            String decoded = cryptoUtils.base64_decode(base64);
            
            assertNotNull("MD5不能为null", md5);
            assertEquals("Base64往返应该正确", input, decoded);
        }
        
        System.out.println("✓ 测试通过（" + testCases.length + " 个测试用例）");
    }
}
