package cn.qaiu.parser;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordLoginRiskTest {

    @Test
    public void securityRiskMessageGuidesAuthorization() {
        JsonObject body = new JsonObject()
                .put("code", 401)
                .put("message", "当前账号存在安全风险，请使用短信验证码或者微信进行登录。");

        assertTrue(PasswordLoginRisk.indicatesCaptchaOrRisk(body));
        String message = PasswordLoginRisk.describeLoginFailure(body);
        assertTrue(message.startsWith(PasswordLoginRisk.AUTHORIZATION_HINT));
        assertTrue(message.contains("验证码/风控"));
        assertTrue(message.contains("auths.ye.authorization"));
        assertTrue(message.contains("authType=authorization"));
        assertTrue(message.contains("authType=accesstoken"));
        assertTrue(message.contains("code=401"));
        assertTrue(message.contains("当前账号存在安全风险"));
        assertFalse(message.startsWith("登录失败:"));
    }

    @Test
    public void overseasRiskAndCaptchaPhrasesMatch() {
        assertRisk("当前账号存在境外登录风险，请使用短信验证码或者微信进行登录。");
        assertRisk("请完成安全验证");
        assertRisk("请进行滑块验证");
        assertRisk("操作频繁，请稍后再试");
        assertRisk("captcha required");
    }

    @Test
    public void wrongPasswordStaysDistinct() {
        JsonObject body = new JsonObject()
                .put("code", 500)
                .put("message", "用户名或密码错误");

        assertFalse(PasswordLoginRisk.indicatesCaptchaOrRisk(body));
        assertEquals("登录失败: 用户名或密码错误", PasswordLoginRisk.describeLoginFailure(body));

        JsonObject account = new JsonObject().put("code", 1).put("msg", "账号或密码错误");
        assertFalse(PasswordLoginRisk.indicatesCaptchaOrRisk(account));
        assertEquals("登录失败: 账号或密码错误", PasswordLoginRisk.describeLoginFailure(account));
    }

    @Test
    public void riskFieldWithoutKeywordStillHints() {
        JsonObject body = new JsonObject()
                .put("code", 200)
                .put("message", "ok")
                .put("data", new JsonObject().put("needCaptcha", true));

        assertFalse(PasswordLoginRisk.isLoginSuccess(body));
        assertTrue(PasswordLoginRisk.indicatesCaptchaOrRisk(body));
        assertTrue(PasswordLoginRisk.describeLoginFailure(body).contains("Authorization"));
    }

    @Test
    public void falseCaptchaFlagDoesNotHint() {
        JsonObject body = new JsonObject()
                .put("code", 500)
                .put("message", "用户名或密码错误")
                .put("data", new JsonObject().put("needCaptcha", false));

        assertFalse(PasswordLoginRisk.indicatesCaptchaOrRisk(body));
        assertEquals("登录失败: 用户名或密码错误", PasswordLoginRisk.describeLoginFailure(body));
    }

    @Test
    public void successTokenIsNotTreatedAsFailure() {
        JsonObject body = new JsonObject()
                .put("code", 200)
                .put("message", "ok")
                .put("data", new JsonObject().put("token", "jwt-token"));

        assertTrue(PasswordLoginRisk.isLoginSuccess(body));
    }

    @Test
    public void blankTokenWithoutRiskKeepsMissingTokenMessage() {
        JsonObject body = new JsonObject()
                .put("code", 200)
                .put("message", "ok")
                .put("data", new JsonObject().put("token", ""));

        assertFalse(PasswordLoginRisk.isLoginSuccess(body));
        assertEquals("未获取到token", PasswordLoginRisk.describeLoginFailure(body));
    }

    @Test
    public void nonJsonCaptchaBodyUsesHint() {
        String body = "<html>请完成滑块验证</html>";
        String message = PasswordLoginRisk.nonJsonLoginFailure(body);
        assertTrue(message.startsWith(PasswordLoginRisk.AUTHORIZATION_HINT));
    }

    @Test
    public void nonJsonWrongPasswordStaysFormatError() {
        assertEquals("登录响应格式异常: 用户名或密码错误",
                PasswordLoginRisk.nonJsonLoginFailure("用户名或密码错误"));
    }

    private static void assertRisk(String message) {
        JsonObject body = new JsonObject().put("code", 5202).put("message", message);
        assertTrue(message, PasswordLoginRisk.indicatesCaptchaOrRisk(body));
        assertTrue(PasswordLoginRisk.describeLoginFailure(body).startsWith(PasswordLoginRisk.AUTHORIZATION_HINT));
    }
}
