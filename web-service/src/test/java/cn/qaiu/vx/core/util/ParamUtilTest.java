package cn.qaiu.vx.core.util;

import org.junit.Test;

import java.util.regex.Pattern;

public class ParamUtilTest {

    @Test
    public void paramsToMap() {
        System.out.println(ParamUtil.paramsToMap(""));
        System.out.println(ParamUtil.paramsToMap("a=asd&d=23"));
        System.out.println(ParamUtil.paramsToMap("asdasd&dd"));

    }

    public static void main(String[] args) {
        Pattern pattern = Pattern.compile("^(/v2/|/json/).*");
    }
}
