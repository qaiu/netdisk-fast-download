package cn.qaiu.vx.core.base;

import io.vertx.core.json.JsonObject;

public interface AppRun {

    /**
     * 执行方法
     * @param config 启动配置文件
     */
    void execute(JsonObject config);
}
