package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Python加密工具类
 * 为Python脚本提供常用的加密解密功能
 *
 * @author QAIU
 */
public class PyCryptoUtils {
    
    private static final Logger log = LoggerFactory.getLogger(PyCryptoUtils.class);
    
    // ==================== MD5 ====================
    
    /**
     * MD5加密（返回32位小写）
     * @param data 待加密数据
     * @return MD5值（32位小写）
     */
    @HostAccess.Export
    public String md5(String data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            log.error("MD5加密失败", e);
            throw new RuntimeException("MD5加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * MD5加密（返回16位小写，取中间16位）
     * @param data 待加密数据
     * @return MD5值（16位小写）
     */
    @HostAccess.Export
    public String md5_16(String data) {
        String md5 = md5(data);
        return md5 != null ? md5.substring(8, 24) : null;
    }
    
    // ==================== SHA ====================
    
    /**
     * SHA-1加密
     * @param data 待加密数据
     * @return SHA-1值（小写）
     */
    @HostAccess.Export
    public String sha1(String data) {
        return sha(data, "SHA-1");
    }
    
    /**
     * SHA-256加密
     * @param data 待加密数据
     * @return SHA-256值（小写）
     */
    @HostAccess.Export
    public String sha256(String data) {
        return sha(data, "SHA-256");
    }
    
    /**
     * SHA-512加密
     * @param data 待加密数据
     * @return SHA-512值（小写）
     */
    @HostAccess.Export
    public String sha512(String data) {
        return sha(data, "SHA-512");
    }
    
    private String sha(String data, String algorithm) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            log.error(algorithm + "加密失败", e);
            throw new RuntimeException(algorithm + "加密失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== Base64 ====================
    
    /**
     * Base64编码
     * @param data 待编码数据
     * @return Base64字符串
     */
    @HostAccess.Export
    public String base64_encode(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Base64编码（字节数组）
     * @param data 待编码字节数组
     * @return Base64字符串
     */
    @HostAccess.Export
    public String base64_encode_bytes(byte[] data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data);
    }
    
    /**
     * Base64解码
     * @param data Base64字符串
     * @return 解码后的字符串
     */
    @HostAccess.Export
    public String base64_decode(String data) {
        if (data == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            throw new RuntimeException("Base64解码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * Base64解码（返回字节数组）
     * @param data Base64字符串
     * @return 解码后的字节数组
     */
    @HostAccess.Export
    public byte[] base64_decode_bytes(String data) {
        if (data == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            throw new RuntimeException("Base64解码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * URL安全的Base64编码
     * @param data 待编码数据
     * @return URL安全的Base64字符串
     */
    @HostAccess.Export
    public String base64_url_encode(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getUrlEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * URL安全的Base64解码
     * @param data URL安全的Base64字符串
     * @return 解码后的字符串
     */
    @HostAccess.Export
    public String base64_url_decode(String data) {
        if (data == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(data);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Base64 URL解码失败", e);
            throw new RuntimeException("Base64 URL解码失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== AES ====================
    
    /**
     * AES加密（ECB模式，PKCS5Padding）
     * @param data 待加密数据
     * @param key 密钥（16/24/32字节）
     * @return Base64编码的密文
     */
    @HostAccess.Export
    public String aes_encrypt_ecb(String data, String key) {
        if (data == null || key == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(padKey(key), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES ECB加密失败", e);
            throw new RuntimeException("AES ECB加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * AES解密（ECB模式，PKCS5Padding）
     * @param data Base64编码的密文
     * @param key 密钥（16/24/32字节）
     * @return 明文
     */
    @HostAccess.Export
    public String aes_decrypt_ecb(String data, String key) {
        if (data == null || key == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(padKey(key), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(data));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES ECB解密失败", e);
            throw new RuntimeException("AES ECB解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * AES加密（CBC模式，PKCS5Padding）
     * @param data 待加密数据
     * @param key 密钥（16/24/32字节）
     * @param iv 初始向量（16字节）
     * @return Base64编码的密文
     */
    @HostAccess.Export
    public String aes_encrypt_cbc(String data, String key, String iv) {
        if (data == null || key == null || iv == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(padKey(key), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(padIv(iv));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES CBC加密失败", e);
            throw new RuntimeException("AES CBC加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * AES解密（CBC模式，PKCS5Padding）
     * @param data Base64编码的密文
     * @param key 密钥（16/24/32字节）
     * @param iv 初始向量（16字节）
     * @return 明文
     */
    @HostAccess.Export
    public String aes_decrypt_cbc(String data, String key, String iv) {
        if (data == null || key == null || iv == null) {
            return null;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(padKey(key), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(padIv(iv));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(data));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES CBC解密失败", e);
            throw new RuntimeException("AES CBC解密失败: " + e.getMessage(), e);
        }
    }
    
    // ==================== Hex ====================
    
    /**
     * 字节数组转十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串（小写）
     */
    @HostAccess.Export
    public String bytes_to_hex(byte[] bytes) {
        return bytesToHex(bytes);
    }
    
    /**
     * 十六进制字符串转字节数组
     * @param hex 十六进制字符串
     * @return 字节数组
     */
    @HostAccess.Export
    public byte[] hex_to_bytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    
    // ==================== 工具方法 ====================
    
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 将密钥填充到16/24/32字节
     */
    private byte[] padKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        int len = keyBytes.length;
        
        // 根据密钥长度决定填充到16/24/32字节
        int targetLen;
        if (len <= 16) {
            targetLen = 16;
        } else if (len <= 24) {
            targetLen = 24;
        } else {
            targetLen = 32;
        }
        
        if (len == targetLen) {
            return keyBytes;
        }
        
        byte[] paddedKey = new byte[targetLen];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(len, targetLen));
        return paddedKey;
    }
    
    /**
     * 将IV填充到16字节
     */
    private byte[] padIv(String iv) {
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        if (ivBytes.length == 16) {
            return ivBytes;
        }
        
        byte[] paddedIv = new byte[16];
        System.arraycopy(ivBytes, 0, paddedIv, 0, Math.min(ivBytes.length, 16));
        return paddedIv;
    }
}
