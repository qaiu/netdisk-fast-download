package cn.qaiu.lz.web.service;

import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface ShoutService extends BaseAsyncService {
    // 提交消息并返回提取码
    Future<String> submitMessage(String content, String host);

    // 通过提取码获取消息
    Future<JsonObject> retrieveMessage(String code);
}
