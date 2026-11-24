package cn.qaiu.parser.impl;

import io.vertx.core.json.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ALTCHA v2（PBKDF2/SHA-256）挑战解算。
 * <p>
 * 口令是 nonce 的原始字节再拼上大端 uint32 计数器，盐是 salt 的原始字节。
 * 找到派生密钥以 keyPrefix 开头的计数器后，按官方 widget 的方式拼出
 * {@code base64({"challenge":{parameters,signature},"solution":{counter,derivedKey,time}})}。
 * 计算是 CPU 密集的，调用方必须放在 Vert.x worker 上，不能占用 event loop。
 */
final class AltchaV2Solver {

    static final int DEFAULT_MAX_COUNTER = 200_000;
    static final long DEFAULT_TIMEOUT_MILLIS = 25_000L;
    /** 城通当前 cost=1200。更高的 cost 会把解析线程占满，直接拒绝。 */
    static final int MAX_COST = 20_000;
    static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_THREADS = 4;

    private AltchaV2Solver() {
    }

    static final class Solution {
        final int counter;
        final String derivedKeyHex;
        final long tookMillis;

        Solution(int counter, String derivedKeyHex, long tookMillis) {
            this.counter = counter;
            this.derivedKeyHex = derivedKeyHex;
            this.tookMillis = tookMillis;
        }
    }

    static Solution solve(JsonObject challenge) {
        return solve(challenge, DEFAULT_MAX_COUNTER, DEFAULT_TIMEOUT_MILLIS);
    }

    static Solution solve(JsonObject challenge, int maxCounter, long timeoutMillis) {
        if (challenge == null || challenge.getJsonObject("parameters") == null) {
            throw new IllegalArgumentException("ALTCHA 挑战为空");
        }
        JsonObject parameters = challenge.getJsonObject("parameters");
        String algorithm = parameters.getString("algorithm", "");
        if (!"PBKDF2/SHA-256".equals(algorithm)) {
            throw new IllegalArgumentException("不支持的 ALTCHA 算法: " + algorithm);
        }
        int cost = intValue(parameters.getValue("cost"), -1);
        int keyLength = intValue(parameters.getValue("keyLength"), 32);
        if (cost < 1 || cost > MAX_COST) {
            throw new IllegalArgumentException("ALTCHA cost 超出可解范围: " + cost);
        }
        if (keyLength < 16 || keyLength > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("ALTCHA keyLength 超出可解范围: " + keyLength);
        }
        byte[] nonce = decodeHex(parameters.getString("nonce", ""), "nonce");
        byte[] salt = decodeHex(parameters.getString("salt", ""), "salt");
        byte[] prefix = decodeHex(parameters.getString("keyPrefix", ""), "keyPrefix");
        if (prefix.length == 0 || prefix.length > keyLength) {
            throw new IllegalArgumentException("ALTCHA keyPrefix 长度无效");
        }
        int limit = Math.max(1, maxCounter);
        long budget = Math.max(1L, timeoutMillis);
        int threads = Math.min(MAX_THREADS, Math.max(1, Runtime.getRuntime().availableProcessors()));
        threads = Math.min(threads, limit);

        AtomicInteger winner = new AtomicInteger(-1);
        AtomicBoolean stop = new AtomicBoolean(false);
        String[] derivedHex = new String[1];
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(budget);
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "ctfile-altcha");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<?>[] tasks = new Future<?>[threads];
            for (int i = 0; i < threads; i++) {
                int start = i;
                int stride = threads;
                tasks[i] = pool.submit((Callable<Void>) () -> {
                    search(start, stride, limit, deadline, nonce, salt, prefix, cost, keyLength, winner, stop, derivedHex);
                    return null;
                });
            }
            for (Future<?> task : tasks) {
                task.get(budget + 2000L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop.set(true);
            throw new IllegalStateException("ALTCHA 解算被中断");
        } catch (IllegalStateException e) {
            stop.set(true);
            throw e;
        } catch (Exception e) {
            stop.set(true);
            throw new IllegalStateException("ALTCHA 解算失败: " + e.getMessage());
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }
        int counter = winner.get();
        if (counter < 0 || derivedHex[0] == null) {
            throw new IllegalStateException("ALTCHA 人机验证解算超时，请稍后重试");
        }
        long took = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return new Solution(counter, derivedHex[0], took);
    }

    /**
     * 官方 altcha widget 成功后提交的 payload：标准 Base64（含填充），不是 URL-safe。
     */
    static String payload(JsonObject challenge, Solution solution) {
        JsonObject body = new JsonObject()
                .put("challenge", new JsonObject()
                        .put("parameters", challenge.getJsonObject("parameters"))
                        .put("signature", challenge.getString("signature")))
                .put("solution", new JsonObject()
                        .put("counter", solution.counter)
                        .put("derivedKey", solution.derivedKeyHex)
                        .put("time", solution.tookMillis));
        return Base64.getEncoder().encodeToString(body.encode().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * PBKDF2-HMAC-SHA256，口令是原始字节。
     * JDK 的 {@code SecretKeyFactory("PBKDF2WithHmacSHA256")} 会把 char[] 口令按 UTF-8 编码，
     * nonce 里大于 0x7F 的字节会被展开，和 ALTCHA / WebCrypto 的原始字节口令对不上。
     */
    static byte[] pbkdf2(byte[] password, byte[] salt, int cost, int keyLength) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password, "HmacSHA256"));
            return pbkdf2(mac, salt, cost, keyLength);
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 计算失败: " + e.getMessage());
        }
    }

    private static void search(int start, int stride, int limit, long deadline, byte[] nonce, byte[] salt,
                               byte[] prefix, int cost, int keyLength, AtomicInteger winner, AtomicBoolean stop,
                               String[] derivedHex) {
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 初始化失败: " + e.getMessage());
        }
        for (int counter = start; counter < limit && winner.get() < 0 && !stop.get(); counter += stride) {
            if ((counter & 31) == start && System.nanoTime() > deadline) {
                return;
            }
            byte[] derived;
            try {
                mac.init(new SecretKeySpec(password(nonce, counter), "HmacSHA256"));
                derived = pbkdf2(mac, salt, cost, keyLength);
            } catch (Exception e) {
                throw new IllegalStateException("PBKDF2 计算失败: " + e.getMessage());
            }
            if (startsWith(derived, prefix) && winner.compareAndSet(-1, counter)) {
                derivedHex[0] = toHex(derived);
                return;
            }
        }
    }

    private static byte[] pbkdf2(Mac mac, byte[] salt, int iterations, int keyLength) throws Exception {
        int hashLength = mac.getMacLength();
        byte[] result = new byte[keyLength];
        byte[] block = new byte[salt.length + 4];
        System.arraycopy(salt, 0, block, 0, salt.length);
        int offset = 0;
        for (int index = 1; offset < keyLength; index++) {
            block[salt.length] = (byte) (index >>> 24);
            block[salt.length + 1] = (byte) (index >>> 16);
            block[salt.length + 2] = (byte) (index >>> 8);
            block[salt.length + 3] = (byte) index;
            byte[] mixed = mac.doFinal(block);
            byte[] digest = mixed.clone();
            for (int round = 1; round < iterations; round++) {
                mixed = mac.doFinal(mixed);
                for (int i = 0; i < mixed.length; i++) {
                    digest[i] ^= mixed[i];
                }
            }
            int length = Math.min(hashLength, keyLength - offset);
            System.arraycopy(digest, 0, result, offset, length);
            offset += length;
        }
        return result;
    }

    private static byte[] password(byte[] nonce, int counter) {
        byte[] password = Arrays.copyOf(nonce, nonce.length + 4);
        password[nonce.length] = (byte) (counter >>> 24);
        password[nonce.length + 1] = (byte) (counter >>> 16);
        password[nonce.length + 2] = (byte) (counter >>> 8);
        password[nonce.length + 3] = (byte) counter;
        return password;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (prefix.length > value.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    static byte[] decodeHex(String hex, String name) {
        if (hex == null || hex.length() % 2 != 0 || !hex.matches("[0-9a-fA-F]*")) {
            throw new IllegalArgumentException("ALTCHA " + name + " 不是合法十六进制");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
