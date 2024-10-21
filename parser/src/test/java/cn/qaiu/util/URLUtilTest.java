package cn.qaiu.util;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class URLUtilTest {

    @Test
    public void getParam() {

        URLUtil util = URLUtil.from("https://i.y.qq.com/v8/playsong.html?ADTAG=cbshare&_wv=1&appshare=android_qq" +
                "&appsongtype=1&appversion=13100008&channelId=10036163&hosteuin=7iosow-s7enz&openinqqmusic=1&platform" +
                "=11&songmid=000XjcLg0fbRjv&type=0");
        Assert.assertEquals(util.getParam("songmid"), "000XjcLg0fbRjv");
        Assert.assertEquals(util.getParam("type"), "0");
    }
}
