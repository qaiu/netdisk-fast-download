package cn.com.yhinfo.core.verticle;

import cn.com.yhinfo.core.annotaions.Service;
import cn.com.yhinfo.core.base.BaseAsyncService;
import cn.com.yhinfo.core.util.ReflectionUtil;
import cn.com.yhinfo.core.util.SharedDataUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务注册到EventBus
 * <br>Create date 2021-05-07 10:26:54
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class ServiceVerticle extends AbstractVerticle {

    Logger LOGGER = LoggerFactory.getLogger(ServiceVerticle.class);
    private static final AtomicInteger ID = new AtomicInteger(1);
    private static final Set<Class<?>> handlers;

    static {
        String handlerLocations = SharedDataUtil.getJsonStringForCustomConfig("handlerLocations");
        Reflections reflections = ReflectionUtil.getReflections(handlerLocations);
        handlers = reflections.getTypesAnnotatedWith(Service.class);
    }

    @Override
    public void start(Promise<Void> startPromise) {
        ServiceBinder binder = new ServiceBinder(vertx);
        if (null != handlers && handlers.size() > 0) {
            handlers.forEach(asyncService -> {
                try {
                    BaseAsyncService asInstance = (BaseAsyncService) ReflectionUtil.newWithNoParam(asyncService);
                    binder.setAddress(asInstance.getAddress()).register(asInstance.getAsyncInterfaceClass(), asInstance);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            });
            LOGGER.info("registered async services -> id: {}", ID.getAndIncrement());
        }
        startPromise.complete();
    }
}
