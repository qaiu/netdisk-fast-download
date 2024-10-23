package cn.qaiu.util;

import io.vertx.core.net.impl.URIDecoder;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final String CIPHER_AES2_IZ = "1uQFS3sNeHd/bCrmrQpflXREAxt0Hn0Ob9v317QirZM=";

    public static final String CIPHER_AES0;
    public static final String CIPHER_AES0_IZ;


    public static final String MG_PKEY2 = "D8jg+H2iNX94zvHhRLnSM3oy59dH2QQjxQ0GgKJSL+mJclbCcItjV3AmkPY6WcbV4hNQk5+hN2J1eTrxPQqF4p28e3FTsGRCXVN80CLS+XqpFNY/9xuyf2bvbeq5JJU1IBCXgSZmEo8zu0/VGS3YNeDsHtjg92QSrRY8i4A+shihZBSz0/0KOL1VPd/K4tAYvsI9YjVFOI7z9mJJ8Ek8rVUplurJyGkjevRfvReN7xQ67PR+yZovk72yTZKlHDz5jVpLGLOy2iwTTSTbTvtnOi9TSE6sSPtRHv16cxZYZQY=";
    public static final String MG_PKEY;

    public static final String MG_KEY = "4ea5c508a6566e76240543f8feb06fd457777be39549c4016436afda65d2330e";

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
            CIPHER_AES0_IZ = decryptByBase64AES(CIPHER_AES2_IZ, CIPHER_AES);
            MG_PKEY = decryptByBase64AES(MG_PKEY2, CIPHER_AES2);
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

    public static String encrypt2HexIz(String source) {
        try {
            return encryptHexByAES(source, CIPHER_AES0_IZ);
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
    private static final char[] arrayIz =
            {'Y', 'y', '0', 'Z', 'z', 'N', 'n', 'M', 'I', '6', 'm', 'W', 'w', '1', 'X', 'x', 'L', 'l', 'K', '7', 'k',
                    'i', 'U', 'u', '2', 'V', 'v', 'J', 'j', '8', 'G', 'g', 'F', 'S', 's', '3', 'T', 't', 'H', 'h',
                    'f', 'E', 'e', 'D', 'Q', 'q', '4', 'R', 'r', '9', 'd', 'a', 'C', 'c', 'B', 'O', 'o', '5', 'P',
                    'p', 'b', 'A'};
    private static int decodeChar(char c, char[] keys) {
        for (int i = 0; i < keys.length; i++) {
            if (c == keys[i]) {
                return i;
            }
        }
        return -1;
    }

    // id解密
    public static int idEncrypt(String str) {
        return idEncrypt0(str, array, 2, 2);
    }


    // ================================蓝奏优享版Id解密========================================== //
    public static int idEncryptIz(String str) {
//        idEncrypt(e) {
//            let t = 1
//                    , n = 0;
//            if ("" != e && e.length > 4) {
//                let r;
//                e = e.substring(3, e.length - 1);
//                for (let v = 0; v < e.length; v++)
//                    r = e.charAt(e.length - v - 1),
//                            n += this.decodeChar(r) * t,
//                            t *= 62
//            }
//            return n
//        },

        return idEncrypt0(str, arrayIz, 3, 1);
    }

    public static int idEncrypt0(String str, char[] keys, int x1, int x2) {
        // 倍数
        int multiple = 1;
        int result = 0;
        if (StringUtils.isNotEmpty(str) && str.length() > 4) {
            str = str.substring(x1, str.length() - x2);
            char c;
            for (int i = 0; i < str.length(); i++) {
                c = str.charAt(str.length() - i - 1);
                result += decodeChar(c, keys) * multiple;
                multiple = multiple * 62;
            }
        }
        return result;
    }



    // ========================== musetransfer加密相关 ===========================

    //length用户要求产生字符串的长度
    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyz0123456789";
        Random random=new Random();
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<length;i++){
            int number=random.nextInt(36);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public static String getRandomString(){
        return getRandomString(10);
    }


    //=============================== 123pan加密相关 ===============================

    public static String getMD5Str(String str) {
        byte[] digest;
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            digest = md5.digest(str.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        //16是表示转换为16进制数
        return new BigInteger(1, digest).toString(16);
    }

    public static String getAuthKey(String _0x2207af) {
        String _0x467baa = "web";
        int _0x4965f1 = 3;

        String _0x430930 = String.valueOf(Math.round(0x989680 * Math.random()));
        String _0x53928f = String.valueOf(new Date().getTime() / 0x3e8);
        String _0x49ec94 = getMD5Str(_0x53928f + "|" + _0x430930 + "|" + _0x2207af + "|" + _0x467baa + "|" + _0x4965f1
                + "|8-8D$sL8gPjom7bk#cY");

        return _0x53928f + "-" + _0x430930 + "-" + _0x49ec94;
    }


    public static String encrypt(String str, String pwd) {
        if (pwd == null || pwd.length() <= 0) {
            throw new IllegalArgumentException("Please enter a password with which to encrypt the message.");
        }

        // 生成 prand 值
        StringBuilder prand = new StringBuilder();
        for (int i = 0; i < pwd.length(); i++) {
            prand.append((int) pwd.charAt(i));
        }

        // 计算 sPos, mult, incr, modu
        int sPos = prand.length() / 5;
        long mult = Long.parseLong(prand.substring(sPos, sPos + 1) +
                prand.substring(sPos * 2, sPos * 2 + 1) +
                prand.substring(sPos * 3, sPos * 3 + 1) +
                prand.substring(sPos * 4, sPos * 4 + 1) +
                prand.substring(sPos * 5, sPos * 5 + 1));
        int incr = (int) Math.ceil(pwd.length() / 2.0);
        long modu = (long) Math.pow(2, 31) - 1;

        if (mult < 2) {
            throw new IllegalArgumentException("Algorithm cannot find a suitable hash. Please choose a different password.");
        }

        // 生成 salt 并加到 prand 上
        long salt = Math.round(Math.random() * 1000000000) % 100000000;
        prand.append(salt);

        // 使用 BigInteger 处理超过 Long 范围的 prand 值
        BigInteger prandValue = new BigInteger(prand.toString());
        while (prandValue.toString().length() > 10) {
            prandValue = prandValue.divide(BigInteger.TEN).add(prandValue.remainder(BigInteger.TEN));
        }

        // 将 prand 转换为 long 进行后续处理
        prandValue = prandValue.multiply(BigInteger.valueOf(mult)).add(BigInteger.valueOf(incr)).mod(BigInteger.valueOf(modu));

        // 加密过程
        StringBuilder encStr = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            // 将字符和异或的结果强制转换为 int
            int encChr = (int) (str.charAt(i) ^ (int) ((prandValue.doubleValue() / modu) * 255));
            String hexStr = Integer.toHexString(encChr);
            if (hexStr.length() < 2) {
                encStr.append("0");
            }
            encStr.append(hexStr);

            prandValue = prandValue.multiply(BigInteger.valueOf(mult)).add(BigInteger.valueOf(incr)).mod(BigInteger.valueOf(modu));
        }

        // 将 salt 转换为 16 进制并追加到加密结果
        String saltHex = Long.toHexString(salt);
        while (saltHex.length() < 8) {
            saltHex = "0" + saltHex;
        }
        encStr.append(saltHex);

        return encStr.toString();
    }

    public static void main(String[] args) {
        // 示例
        String encrypted = encrypt("HelloWorld", "password123");
        System.out.println("Encrypted String: " + encrypted);
    }

}
