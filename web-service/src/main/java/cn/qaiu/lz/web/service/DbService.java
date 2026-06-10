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
     * 获取启动时需要注册的已启用演练场解析器
     */
    Future<JsonObject> getEnabledPlaygroundParsersForLoad();

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
     * 检查演练场解析器类型是否已存在
     */
    Future<Boolean> playgroundParserTypeExists(String type, Long excludeId);

    /**
     * 根据ID获取演练场解析器
     */
    Future<JsonObject> getPlaygroundParserById(Long id);

    // ========== 捐赠账号相关 ==========

    /**
     * 保存捐赠账号
     */
    Future<JsonObject> saveDonatedAccount(JsonObject account);

    /**
     * 获取各网盘捐赠账号数量统计
     */
    Future<JsonObject> getDonatedAccountCounts();

    /**
     * 随机获取指定网盘类型的一个启用账号
     */
    Future<JsonObject> getRandomDonatedAccount(String panType);

    /**
     * 签发捐赠账号失败计数令牌（服务端临时令牌）
     */
    Future<String> issueDonatedAccountFailureToken(Long accountId);

    /**
     * 使用服务端失败计数令牌记录捐赠账号解析失败
     */
    Future<Void> recordDonatedAccountFailureByToken(String failureToken);
}
