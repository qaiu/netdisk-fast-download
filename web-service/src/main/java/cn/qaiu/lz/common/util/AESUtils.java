package cn.qaiu.lz.common.util;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES加解密工具类
 *
 * @author qaiu
 **/
public class AESUtils {

    /**
     * AES密钥标识
     */
    public static final String SIGN_AES = "AES";

    /**
     * 密码器AES模式
     */
    public static final String CIPHER_AES = "AES/ECB/PKCS5Padding";

    public static final String CIPHER_AES2 = "YbQHZqK/PdQql2+7ATcPQHREAxt0Hn0Ob9v317QirZM=";

    public static final String CIPHER_AES0;

    /**
     * 秘钥长度
     */
    public static final int KEY_LENGTH = 16;

    /**
     * 密钥长度128
     */
    public static final int KEY_SIZE_128_LENGTH = 128;

    /**
     * 密钥长度192
     */
    public static final int KEY_SIZE_192_LENGTH = 192;

    /**
     * 密钥长度256
     */
    public static final int KEY_SIZE_256_LENGTH = 256;

    static {
        try {
            CIPHER_AES0 = decryptByBase64AES(CIPHER_AES2, CIPHER_AES);
        } catch (IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 随机生成密钥，请使用合适的长度128 192 256
     */
    public static Key createKeyString(int keySize) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(SIGN_AES);
        keyGenerator.init(keySize);
        SecretKey secretKey = keyGenerator.generateKey();
        return new SecretKeySpec(secretKey.getEncoded(), SIGN_AES);
    }

    /**
     * 生成Key对象
     */
    public static Key generateKey(String keyString) {
        if (keyString.length() > KEY_LENGTH) {
            keyString = keyString.substring(0, KEY_LENGTH);
        } else if (keyString.length() < KEY_LENGTH) {
            keyString = StringUtils.rightPad(keyString, 16, 'L');
        }
        return new SecretKeySpec(keyString.getBytes(), SIGN_AES);
    }

    /**
     * AES加密
     *
     * @param source    原文
     * @param keyString 秘钥
     * @return byte arrays
     */
    public static byte[] encryptByAES(String source, String keyString) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(CIPHER_AES);
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(keyString));
        return cipher.doFinal(source.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encryptByAES(String source, Key key) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(CIPHER_AES);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * AES加密Base64
     *
     * @param source    原文
     * @param keyString 秘钥
     * @return BASE64
     */
    public static String encryptBase64ByAES(String source, String keyString) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encrypted = encryptByAES(source, keyString);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String encryptBase64ByAES(String source, Key key) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encrypted = encryptByAES(source, key);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * AES加密Hex
     *
     * @param source    原文
     * @param keyString 秘钥
     */
    public static String encryptHexByAES(String source, String keyString) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encrypted = encryptByAES(source, keyString);
        return HexFormat.of().formatHex(encrypted);
    }

    public static String encryptHexByAES(String source, Key key) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encrypted = encryptByAES(source, key);
        return HexFormat.of().formatHex(encrypted);
    }

    public static String encrypt2Hex(String source) {
        try {
            return encryptHexByAES(source, CIPHER_AES0);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException("加密失败: "+ e.getMessage());
        }
    }

    /**
     * AES解密
     *
     * @param encrypted 密文 byte
     * @param keyString 秘钥
     */
    public static String decryptByAES(byte[] encrypted, String keyString) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        return decryptByAES(encrypted, generateKey(keyString));
    }

    public static String decryptByAES(byte[] encrypted, Key key) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance(CIPHER_AES);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES解密
     *
     * @param encrypted 密文 Hex
     * @param keyString 秘钥
     */
    public static String decryptByHexAES(String encrypted, String keyString) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        return decryptByAES(HexFormat.of().parseHex(encrypted), keyString);
    }

    public static String decryptByHexAES(String encrypted, Key key) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        return decryptByAES(HexFormat.of().parseHex(encrypted), key);
    }

    /**
     * AES解密
     *
     * @param encrypted 密文 Base64
     * @param keyString 秘钥
     */
    public static String decryptByBase64AES(String encrypted, String keyString) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        return decryptByAES(Base64.getDecoder().decode(encrypted), keyString);
    }

    public static String decryptByBase64AES(String encrypted, Key key) throws IllegalBlockSizeException,
            BadPaddingException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        return decryptByAES(Base64.getDecoder().decode(encrypted), key);
    }

    // ================================飞机盘Id解密========================================== //
    private static final char[] array = {
            'T', 'U', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            '0', 'M', 'N', 'O', 'P', 'X', 'Y', 'Z', 'V', 'W',
            'Q', '1', '2', '3', '4', 'a', 'b', 'c', 'd', 'e',
            '5', '6', '7', '8', '9', 'v', 'w', 'x', 'y', 'z',
            'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
            'p', 'q', 'r', 's', 't', 'u', 'L', 'R', 'S', 'I',
            'J', 'K'};

    private static int decodeChar(char c) {
        for (int i = 0; i < array.length; i++) {
            if (c == array[i]) {
                return i;
            }
        }
        return -1;
    }

    // id解密
    public static int idEncrypt(String str) {
        // 倍数
        int multiple = 1;
        int result = 0;
        if (StringUtils.isNotEmpty(str) && str.length() > 4) {
            str = str.substring(2, str.length() - 2);
            char c;
            for (int i = 0; i < str.length(); i++) {
                c = str.charAt(str.length() - i - 1);
                result += decodeChar(c) * multiple;
                multiple = multiple * 62;
            }
        }
        return result;
    }
}
