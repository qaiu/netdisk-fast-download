package cn.qaiu.lz.web.util;

import cn.qaiu.util.Base64Decoder;
import cn.qaiu.vx.core.util.ConfigUtil;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtil {

    private static final Logger logger = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits

    private static Future<SecretKeySpec> secretKeyFuture;

    static {
        Vertx vertx = VertxHolder.getVertxInstance();
        if (vertx != null) {
            secretKeyFuture = ConfigUtil.readYamlConfig("secret", vertx)
                    .map(config -> {
                        String key = config.getJsonObject("encrypt").getString("key");
                        if (key != null) {
                            key = key.trim();
                        }
                        byte[] keyBytes = key == null ? null : key.getBytes(StandardCharsets.UTF_8);
                        if (keyBytes == null || keyBytes.length != 32) {
                            int currentLen = keyBytes == null ? 0 : keyBytes.length;
                            throw new IllegalArgumentException("Invalid AES key length in secret.yml. Key must be 32 bytes. current=" + currentLen);
                        }
                        return new SecretKeySpec(keyBytes, "AES");
                    })
                    .onFailure(err -> logger.error("Failed to load encryption key from secret.yml", err));
        } else {
            logger.error("Vertx instance is not available for CryptoUtil initialization.");
            secretKeyFuture = Future.failedFuture("Vertx instance not available.");
        }
    }

    public static Future<String> encrypt(String strToEncrypt) {
        if (strToEncrypt == null) {
            return Future.succeededFuture(null);
        }
        return secretKeyFuture.compose(secretKey -> {
            try {
                byte[] iv = new byte[GCM_IV_LENGTH];
                SecureRandom random = new SecureRandom();
                random.nextBytes(iv);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

                byte[] cipherText = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

                // Prepend IV to ciphertext
                ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
                byteBuffer.put(iv);
                byteBuffer.put(cipherText);

                return Future.succeededFuture(Base64.getEncoder().encodeToString(byteBuffer.array()));
            } catch (Exception e) {
                return Future.failedFuture(new CryptoException("Encryption failed", e));
            }
        });
    }

    public static Future<String> decrypt(String strToDecrypt) {
        if (strToDecrypt == null) {
            return Future.succeededFuture(null);
        }
        return secretKeyFuture.compose(secretKey -> {
            try {
                byte[] decodedBytes = Base64Decoder.decode(strToDecrypt);

                // Extract IV from the beginning of the decoded bytes
                ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
                byte[] iv = new byte[GCM_IV_LENGTH];
                byteBuffer.get(iv);
                byte[] cipherText = new byte[byteBuffer.remaining()];
                byteBuffer.get(cipherText);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

                byte[] decryptedText = cipher.doFinal(cipherText);

                return Future.succeededFuture(new String(decryptedText, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return Future.failedFuture(new CryptoException("Decryption failed", e));
            }
        });
    }
}
