package cn.qaiu.parser.impl;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 123 登录提示。不访问网络。
 * mvn -pl parser -DskipTests=false -Dtest=Ye2LoginHintTest test
 */
public class Ye2LoginHintTest {

    @Test
    public void loginCodesAndMessagesAreRecognized() {
        assertTrue(Ye2Tool.isYeLoginRequired(5112, "您需要注册登录或付费后下载"));
        assertTrue(Ye2Tool.isYeLoginRequired(401, "请登录后下载"));
        assertTrue(Ye2Tool.isYeLoginRequired(0, "需要登录才能下载"));
        assertFalse(Ye2Tool.isYeLoginRequired(0, "ok"));
        assertFalse(Ye2Tool.isYeLoginRequired(null, null));
    }

    @Test
    public void missingAuthTellsUserToConfigureLocalAuthorization() {
        String msg = Ye2Tool.loginRequiredDownloadMessage(false, null);
        assertTrue(msg.contains("auths.ye.authorization"));
        assertTrue(msg.contains("5112"));
        assertTrue(msg.contains("公共云解析"));
        assertTrue(msg.contains("ye:分享key@提取码"));
        assertFalse(msg.contains("验证码求解"));
    }

    @Test
    public void configuredAuthStillPointsAtLocalTokenWhenUpstreamRejects() {
        String msg = Ye2Tool.loginRequiredDownloadMessage(true, "请登录后下载");
        assertTrue(msg.contains("auths.ye.authorization"));
        assertTrue(msg.contains("上游信息: 请登录后下载"));
        assertFalse(msg.contains("{"));
    }
}
