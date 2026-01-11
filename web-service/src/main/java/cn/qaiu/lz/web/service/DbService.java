package cn.qaiu.lz.web.service;

import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * lz-web
 * <br>Create date 2021/7/12 17:16
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface DbService extends BaseAsyncService {
    Future<JsonObject> sayOk(String data);
    Future<JsonObject> sayOk2(String data, UserInfo holder);

    Future<StatisticsInfo> getStatisticsInfo();

    /**
     * 获取演练场解析器列表
     */
    Future<JsonObject> getPlaygroundParserList();

    /**
     * 保存演练场解析器
     */
    Future<JsonObject> savePlaygroundParser(JsonObject parser);

    /**
     * 更新演练场解析器
     */
    Future<JsonObject> updatePlaygroundParser(Long id, JsonObject parser);

    /**
     * 删除演练场解析器
     */
    Future<JsonObject> deletePlaygroundParser(Long id);

    /**
     * 获取演练场解析器数量
     */
    Future<Integer> getPlaygroundParserCount();

    /**
     * 根据ID获取演练场解析器
     */
    Future<JsonObject> getPlaygroundParserById(Long id);

    /**
     * 根据type查询解析器是否存在
     */
    Future<Boolean> existsPlaygroundParserByType(String type);

    /**
     * 初始化示例解析器（JS和Python）
     */
    Future<Void> initExampleParsers();

}
