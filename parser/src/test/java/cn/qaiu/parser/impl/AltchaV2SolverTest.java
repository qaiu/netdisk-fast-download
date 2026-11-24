package cn.qaiu.parser.impl;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AltchaV2SolverTest {

    private static final String NONCE = "00112233445566778899aabbccddeeff";
    private static final String SALT = "0102030405060708090a0b0c0d0e0f10";

    @Test
    public void pbkdf2MatchesAltchaVector() {
        byte[] password = password(NONCE, 7);
        byte[] derived = AltchaV2Solver.pbkdf2(password, AltchaV2Solver.decodeHex(SALT, "salt"), 100, 32);
        assertEquals("dce8b41d12bd8245a12f9fbca56d1d5db2924c1bfe02b924be69f6d89dc1721b",
                AltchaV2Solver.toHex(derived));
    }

    @Test
    public void productionCostMatchesCounterZero() {
        byte[] derived = AltchaV2Solver.pbkdf2(password(NONCE, 0),
                AltchaV2Solver.decodeHex(SALT, "salt"), 1200, 32);
        assertEquals("c24bb34595d0df8688c53e6be3b63e4a0fb0f2d9f732b5c4daebc46fba958bf7",
                AltchaV2Solver.toHex(derived));
    }

    @Test
    public void solverFindsPrefixedCounterAndBuildsPayload() {
        byte[] derived = AltchaV2Solver.pbkdf2(password(NONCE, 3),
                AltchaV2Solver.decodeHex(SALT, "salt"), 50, 32);
        String prefix = AltchaV2Solver.toHex(derived).substring(0, 8);
        JsonObject challenge = challenge(50, prefix, "sig-test");
        AltchaV2Solver.Solution solution = AltchaV2Solver.solve(challenge, 20, 5_000);
        assertEquals(3, solution.counter);
        assertTrue(solution.derivedKeyHex.startsWith(prefix));

        JsonObject payload = new JsonObject(new String(Base64.getDecoder().decode(AltchaV2Solver.payload(challenge, solution))));
        assertEquals("sig-test", payload.getJsonObject("challenge").getString("signature"));
        assertEquals("PBKDF2/SHA-256", payload.getJsonObject("challenge").getJsonObject("parameters").getString("algorithm"));
        assertEquals(3, payload.getJsonObject("solution").getInteger("counter").intValue());
        assertEquals(solution.derivedKeyHex, payload.getJsonObject("solution").getString("derivedKey"));
    }

    @Test
    public void impossiblePrefixStopsAtTheCounterCap() {
        byte[] beyondCap = AltchaV2Solver.pbkdf2(password(NONCE, 40),
                AltchaV2Solver.decodeHex(SALT, "salt"), 20, 32);
        JsonObject challenge = challenge(20, AltchaV2Solver.toHex(beyondCap).substring(0, 8), "sig");
        try {
            AltchaV2Solver.solve(challenge, 8, 5_000);
            fail("expected the search to stop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("超时"));
        }
    }

    @Test
    public void rejectsCostAboveTheSafetyCap() {
        JsonObject challenge = challenge(AltchaV2Solver.MAX_COST + 1, "00", "sig");
        try {
            AltchaV2Solver.solve(challenge, 1, 1_000);
            fail("expected cost to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cost"));
        }
    }

    private static JsonObject challenge(int cost, String keyPrefix, String signature) {
        return new JsonObject()
                .put("parameters", new JsonObject()
                        .put("algorithm", "PBKDF2/SHA-256")
                        .put("cost", cost)
                        .put("keyLength", 32)
                        .put("nonce", NONCE)
                        .put("salt", SALT)
                        .put("keyPrefix", keyPrefix))
                .put("signature", signature);
    }

    private static byte[] password(String nonceHex, int counter) {
        byte[] nonce = AltchaV2Solver.decodeHex(nonceHex, "nonce");
        byte[] password = new byte[nonce.length + 4];
        System.arraycopy(nonce, 0, password, 0, nonce.length);
        password[nonce.length] = (byte) (counter >>> 24);
        password[nonce.length + 1] = (byte) (counter >>> 16);
        password[nonce.length + 2] = (byte) (counter >>> 8);
        password[nonce.length + 3] = (byte) counter;
        return password;
    }
}
