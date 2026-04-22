package cn.qaiu.vx.core.verticle;

import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.base.BaseAsyncService;
import cn.qaiu.vx.core.util.ReflectionUtil;
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
        Reflections reflections = ReflectionUtil.getReflections();
        handlers = reflections.getTypesAnnotatedWith(Service.class);
    }
    @Override
    public void start(Promise<Void> startPromise) {
        ServiceBinder binder = new ServiceBinder(vertx);
        if (null != handlers && handlers.size() > 0) {
            // handlers转为拼接类列表，xxx,yyy,zzz
            StringBuilder serviceNames = new StringBuilder();
            handlers.forEach(asyncService -> {
                try {
                    serviceNames.append(asyncService.getName()).append("|");
                    BaseAsyncService asInstance = (BaseAsyncService) ReflectionUtil.newWithNoParam(asyncService);
                    binder.setAddress(asInstance.getAddress()).register(asInstance.getAsyncInterfaceClass(), asInstance);
                } catch (Exception e) {
                    LOGGER.error("Failed to register service: {}", asyncService.getName(), e);
                }
            });

            LOGGER.info("registered async services -> id: {}, name: {}", ID.getAndIncrement(), serviceNames.toString());
        }
        startPromise.complete();
    }
}
