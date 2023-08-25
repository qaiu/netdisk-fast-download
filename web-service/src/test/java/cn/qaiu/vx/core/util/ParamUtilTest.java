package cn.qaiu.vx.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParamUtilTest {

    @Test
    public void paramsToMap() {
        System.out.println(ParamUtil.paramsToMap(""));
        System.out.println(ParamUtil.paramsToMap("a=asd&d=23"));
        System.out.println(ParamUtil.paramsToMap("asdasd&dd"));
    }
}
