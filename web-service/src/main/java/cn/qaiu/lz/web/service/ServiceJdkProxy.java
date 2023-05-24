package cn.qaiu.lz.web.service;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * lz-web
 * <br>Create date 2021/8/25 14:28
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
public class ServiceJdkProxy<T> implements InvocationHandler {

    private final T target;

    public ServiceJdkProxy(T target) {
        this.target = target;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(target, args);
    }
}
