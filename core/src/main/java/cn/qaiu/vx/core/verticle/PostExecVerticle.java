package cn.qaiu.vx.core.verticle;

import cn.qaiu.vx.core.base.AppRun;
import cn.qaiu.vx.core.base.DefaultAppRun;
import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.ReflectionUtil;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后置执行Verticle - 在core启动后立即执行AppRun实现
 * <br>Create date 2024-01-01 00:00:00
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class PostExecVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostExecVerticle.class);
    private static final Set<AppRun> appRunImplementations;
    private static final AtomicBoolean lock = new AtomicBoolean(false);

    static {
        Reflections reflections = ReflectionUtil.getReflections();
        Set<Class<? extends AppRun>> subTypesOf = reflections.getSubTypesOf(AppRun.class);
        subTypesOf.add(DefaultAppRun.class);
        appRunImplementations = CommonUtil.sortClassSet(subTypesOf);
        if (appRunImplementations.isEmpty()) {
            LOGGER.warn("未找到 AppRun 接口的实现类");
        } else {
            LOGGER.info("找到 {} 个 AppRun 接口的实现类", appRunImplementations.size());
        }
    }

    @Override
    public void start(Promise<Void> startPromise) {
        if (!lock.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info("PostExecVerticle 开始执行...");
        
        if (appRunImplementations != null && !appRunImplementations.isEmpty()) {
            appRunImplementations.forEach(appRun -> {
                try {
                    LOGGER.info("执行 AppRun 实现: {}", appRun.getClass().getName());
                    JsonObject globalConfig = SharedDataUtil.getJsonConfig("globalConfig");
                    appRun.execute(globalConfig);
                    LOGGER.info("AppRun 实现 {} 执行完成", appRun.getClass().getName());
                } catch (Exception e) {
                    LOGGER.error("执行 AppRun 实现 {} 时发生错误",appRun.getClass().getName(), e);
                }
            });
        } else {
            LOGGER.info("未找到 AppRun 接口的实现类");
        }
        
        LOGGER.info("PostExecVerticle 执行完成");
        startPromise.complete();
    }
}