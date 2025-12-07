package cn.qaiu.lz;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.config.PlaygroundConfig;
import cn.qaiu.lz.common.interceptorImpl.RateLimiter;
import cn.qaiu.vx.core.Deploy;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.shareddata.LocalMap;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;

import static cn.qaiu.vx.core.util.ConfigConstant.LOCAL;


/**
 * vertx程序入口
 * 
 * <br>Create date 2021-05-08 13:00:01
 * @author qaiu yyzy
 */
public class AppMain {

    public static void main(String[] args) {
        // start
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
        // 限流
        if (jsonObject.containsKey("rateLimit")) {
            JsonObject rateLimit = jsonObject.getJsonObject("rateLimit");
            RateLimiter.init(rateLimit);
        }
        // 数据库
        if (jsonObject.getJsonObject(ConfigConstant.SERVER).getBoolean("enableDatabase")) {
            JDBCPoolInit.builder().config(jsonObject.getJsonObject("dataSource"))
                    .build()
                    .initPool().onSuccess(PreparedStatement -> {
                        VertxHolder.getVertxInstance().setTimer(1000, id -> {
                            System.out.println(DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS"));
                            System.out.println("数据库连接成功");
                            String addr = jsonObject.getJsonObject(ConfigConstant.SERVER).getString("domainName");
                            System.out.println("启动成功: \n本地服务地址: " + addr);
                        });
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

        // 认证
        if (jsonObject.containsKey(ConfigConstant.AUTHS)) {
            JsonObject auths = jsonObject.getJsonObject(ConfigConstant.AUTHS);
            localMap.put(ConfigConstant.AUTHS, auths);
        }

        // Playground配置
        if (jsonObject.containsKey("playground")) {
            PlaygroundConfig.init(jsonObject.getJsonObject("playground"));
        }
    }
}
