package cn.com.yhinfo.real.web.service;

import cn.com.yhinfo.core.util.CastUtil;

import java.lang.reflect.Proxy;

/**
 * JDK代理类工厂
 */
public class JdkProxyFactory {
    public static <T> T getProxy(T target) {
        return CastUtil.cast(Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new ServiceJdkProxy<>(target))
        );
    }
}