package cn.qaiu.util;

import org.junit.Assert;
import org.junit.Test;

import static cn.qaiu.util.AcwScV2Generator.acwScV2Simple;

public class AcwScV2GeneratorTest {

    // 简单测试
    @Test
    public void testCookie() {
        String arg1 = "3E40CCD6747C0E55B0531DB86380DDA1D08CE247";
        String cookie = acwScV2Simple(arg1);
        Assert.assertEquals("68d8c25247df18dd66d24165d11084d09bc00db9", cookie);
    }

}