package cn.qaiu.vx.core.base;

import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的AppRun实现示例
 * <br>Create date 2024-01-01 00:00:00
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@HandleSortFilter
public class DefaultAppRun implements AppRun {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAppRun.class);

    @Override
    public void execute(JsonObject config) {
        LOGGER.info("======> AppRun实现类开始执行，配置数: {}", config.size());
    }
}
