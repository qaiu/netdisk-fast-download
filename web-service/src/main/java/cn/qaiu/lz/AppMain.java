package cn.qaiu.lz;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.vx.core.Deploy;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.shareddata.LocalMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static cn.qaiu.vx.core.util.ConfigConstant.LOCAL;


/**
 * 程序入口
 * <br>Create date 2021-05-08 13:00:01
 *
 * @author qaiu
 */
public class AppMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppMain.class);

    public static void main(String[] args) {
        Deploy.instance().start(args, AppMain::exec);
    }

    /**
     * 框架回调方法
     * 初始化数据库/缓存等
     *
     * @param jsonObject 配置
     */
    private static void exec(JsonObject jsonObject) {
        WebClientVertxInit.init(VertxHolder.getVertxInstance());
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        // 数据库
        if (jsonObject.getJsonObject(ConfigConstant.SERVER).getBoolean("enableDatabase")) {
            JDBCPoolInit.builder().config(jsonObject.getJsonObject("dataSource"))
                    .build()
                    .initPool().onSuccess(PreparedStatement -> {
                        LOGGER.info("数据库连接成功");
                        String addr = jsonObject.getJsonObject(ConfigConstant.SERVER).getString("domainName");
                        LOGGER.info("启动成功: \n本地服务地址: {}", addr);
                    });
        }
        // 缓存
        if (jsonObject.containsKey(ConfigConstant.CACHE)) {
            CacheConfigLoader.init(jsonObject.getJsonObject(ConfigConstant.CACHE));
        }

        LocalMap<Object, Object> localMap = VertxHolder.getVertxInstance().sharedData().getLocalMap(LOCAL);
        // 代理
        if (jsonObject.containsKey(ConfigConstant.PROXY)) {
            JsonArray proxyJsonArray = jsonObject.getJsonArray(ConfigConstant.PROXY);
            if (proxyJsonArray != null) {
                JsonObject jsonObject1 = new JsonObject();
                proxyJsonArray.forEach(proxyJson -> {
                    String panTypes = ((JsonObject)proxyJson).getString("panTypes");

                    if (!panTypes.isEmpty()) {
                        for (String s : panTypes.split(",")) {
                            jsonObject1.put(s, proxyJson);
                        }
                    }
                });
                localMap.put("proxy", jsonObject1);
            }
        }
    }
}
