package cn.qaiu.lz;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.cache.CacheConfigLoader;
import cn.qaiu.lz.common.interceptorImpl.RateLimiter;
import cn.qaiu.lz.web.config.PlaygroundConfig;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.parser.custom.CustomParserConfig;
import cn.qaiu.parser.custom.CustomParserRegistry;
import cn.qaiu.parser.customjs.JsScriptMetadataParser;
import cn.qaiu.vx.core.Deploy;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.shareddata.LocalMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Date;

import static cn.qaiu.vx.core.util.ConfigConstant.LOCAL;


/**
 * vertx程序入口
 * 
 * <br>Create date 2021-05-08 13:00:01
 * @author qaiu yyzy
 */
@Slf4j
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
                            
                            // 加载演练场解析器
                            loadPlaygroundParsers();
                            
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
        
        // 演练场配置
        PlaygroundConfig.loadFromJson(jsonObject);
    }
    
    /**
     * 在启动时加载所有已发布的演练场解析器
     */
    private static void loadPlaygroundParsers() {
        DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);
        
        dbService.getPlaygroundParserList().onSuccess(result -> {
            JsonArray parsers = result.getJsonArray("data");
            if (parsers != null) {
                int loadedCount = 0;
                for (int i = 0; i < parsers.size(); i++) {
                    JsonObject parser = parsers.getJsonObject(i);
                    
                    // 只注册已启用的解析器
                    if (parser.getBoolean("enabled", false)) {
                        try {
                            String jsCode = parser.getString("jsCode");
                            if (jsCode == null || jsCode.trim().isEmpty()) {
                                log.error("加载演练场解析器失败: {} - JavaScript代码为空", parser.getString("name"));
                                continue;
                            }
                            CustomParserConfig config = JsScriptMetadataParser.parseScript(jsCode);
                            CustomParserRegistry.register(config);
                            loadedCount++;
                            log.info("已加载演练场解析器: {} ({})", 
                                    config.getDisplayName(), config.getType());
                        } catch (Exception e) {
                            String parserName = parser.getString("name");
                            String errorMsg = e.getMessage();
                            log.error("加载演练场解析器失败: {} - {}", parserName, errorMsg, e);
                            // 如果是require相关错误，提供更详细的提示
                            if (errorMsg != null && errorMsg.contains("require")) {
                                log.error("提示：演练场解析器不支持CommonJS模块系统（require），请确保代码使用ES5.1语法");
                            }
                        }
                    }
                }
                log.info("演练场解析器加载完成，共加载 {} 个解析器", loadedCount);
            } else {
                log.info("未找到已发布的演练场解析器");
            }
        }).onFailure(e -> {
            log.error("加载演练场解析器列表失败", e);
        });
    }
}
