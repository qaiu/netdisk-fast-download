package cn.qaiu.vx.core;

import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.ConfigUtil;
import cn.qaiu.vx.core.util.VertxHolder;
import cn.qaiu.vx.core.verticle.HttpProxyVerticle;
import cn.qaiu.vx.core.verticle.PostExecVerticle;
import cn.qaiu.vx.core.verticle.ReverseProxyVerticle;
import cn.qaiu.vx.core.verticle.RouterVerticle;
import cn.qaiu.vx.core.verticle.ServiceVerticle;
import io.vertx.core.*;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static cn.qaiu.vx.core.util.ConfigConstant.*;

/**
 * vertx启动类 需要在主启动类完成回调
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class Deploy {

    private static final Deploy INSTANCE = new Deploy();
    private static final Logger LOGGER = LoggerFactory.getLogger(Deploy.class);
    private static final long startTime = System.currentTimeMillis();

    private final Vertx tempVertx = Vertx.vertx();
    StringBuilder path = new StringBuilder("app");

    private JsonObject customConfig;
    private JsonObject globalConfig;
    private Handler<JsonObject> handle;

    private Thread mainThread;

    public static Deploy instance() {
        return INSTANCE;
    }

    /**
     *
     * @param args 启动参数
     * @param handle 启动完成后回调处理函数
     */
    public void start(String[] args, Handler<JsonObject> handle) {
        this.mainThread = Thread.currentThread();
        this.handle = handle;

        if (args.length > 0 && args[0].startsWith("app-")) {
            // 启动参数dev或者prod
            path.append("-").append(args[0].replace("app-",""));
        }

        // 读取yml配置
        ConfigUtil.readYamlConfig(path.toString(), tempVertx)
                .onSuccess(this::readConf)
                .onFailure(Throwable::printStackTrace);
        LockSupport.park();
        deployVerticle();
    }

    private void readConf(JsonObject conf) {
        outLogo(conf);
        var activeMode = conf.getString("active");
        if ("dev".equals(activeMode)) {
            LOGGER.info("---------------> development environment <--------------\n");
            System.setProperty("vertxweb.environment", "dev");
        } else {
            LOGGER.info("---------------> Production environment <--------------\n");
        }
        ConfigUtil.readYamlConfig(path + "-" + activeMode, tempVertx).onSuccess(res -> {
            this.globalConfig = res;
            LockSupport.unpark(mainThread);
        });
    }

    /**
     * 打印logo
     */
    private void outLogo(JsonObject conf) {
        var calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        var year = calendar.get(Calendar.YEAR);
        var logoTemplate = """

                Web Server powered by:\s
                 ____   ____              _              _    _   \s
                |_^^_| |_^^_|            / |_           | |  | |  \s
                  \\ \\   / /.---.  _ .--.`| |-'   _   __ | |__| |_ \s
                   \\ \\ / // /__\\\\[ `/'`\\]| |    [ \\ [  ]|____   _|\s
                    \\ V / | \\__., | |    | |, _  > '  <     _| |_ \s
                     \\_/   '.__.'[___]   \\__/(_)[__]`\\_]   |_____|\s
                                                      Version: %s; Framework version: %s; %s©%d.

                """;

        System.out.printf(logoTemplate,
                CommonUtil.getAppVersion(),
                "4x",
                conf.getString("copyright"),
                year
        );
    }

    /**
     * 部署Verticle
     */
    private void deployVerticle() {
        tempVertx.close();
        LOGGER.info("配置读取成功");
        customConfig = globalConfig.getJsonObject(CUSTOM);

        JsonObject vertxConfig = globalConfig.getJsonObject(VERTX);
        Integer vertxConfigELPS = vertxConfig.getInteger(EVENT_LOOP_POOL_SIZE);
        var vertxOptions = vertxConfigELPS == 0 ?
                new VertxOptions() : new VertxOptions(vertxConfig);

//        vertxOptions.setAddressResolverOptions(
//                new AddressResolverOptions().
//                        addServer("114.114.114.114").
//                        addServer("114.114.115.115").
//                        addServer("8.8.8.8").
//                        addServer("8.8.4.4"));
        LOGGER.info("vertxConfigEventLoopPoolSize: {}, eventLoopPoolSize: {}, workerPoolSize: {}", vertxConfigELPS,
                vertxOptions.getEventLoopPoolSize(),
                vertxOptions.getWorkerPoolSize());
        var vertx = Vertx.vertx(vertxOptions);
        VertxHolder.init(vertx);
        //配置保存在共享数据中
        var sharedData = vertx.sharedData();
        LocalMap<String, Object> localMap = sharedData.getLocalMap(LOCAL);
        localMap.put(GLOBAL_CONFIG, globalConfig);
        localMap.put(CUSTOM_CONFIG, customConfig);
        localMap.put(SERVER, globalConfig.getJsonObject(SERVER));
        var future0 = vertx.createSharedWorkerExecutor("other-handle")
                .executeBlocking(() -> {
                    handle.handle(globalConfig);
                    return "Other handle complete";
                });

        future0.onSuccess(res -> {
            LOGGER.info(res);
            // 部署 路由、异步service、反向代理 服务
            var future1 = vertx.deployVerticle(RouterVerticle.class, getWorkDeploymentOptions("Router"));
            var future2 = vertx.deployVerticle(ServiceVerticle.class, getWorkDeploymentOptions("Service"));
            var future3 = vertx.deployVerticle(ReverseProxyVerticle.class, getWorkDeploymentOptions("proxy"));


            JsonObject jsonObject = ((JsonObject) localMap.get(GLOBAL_CONFIG)).getJsonObject("proxy-server");
            if (jsonObject != null) {
                genPwd(jsonObject);
                var future4 = vertx.deployVerticle(HttpProxyVerticle.class, getWorkDeploymentOptions("proxy"));
                future4.onSuccess(LOGGER::info);
                future4.onFailure(e -> LOGGER.error("Other handle error", e));
                Future.all(future1, future2, future3, future4)
                        .onSuccess(this::deployWorkVerticalSuccess)
                        .onFailure(this::deployVerticalFailed);
            } else {
                Future.all(future1, future2, future3)
                        .onSuccess(this::deployWorkVerticalSuccess)
                        .onFailure(this::deployVerticalFailed);
            }

        }).onFailure(e -> LOGGER.error("Other handle error", e));
    }

    private static void genPwd(JsonObject jsonObject) {
        if (jsonObject.getBoolean("randUserPwd")) {
            var username = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            var password = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            jsonObject.put("username", username);
            jsonObject.put("password", password);
        }
        LOGGER.info("=============server info=================");
        LOGGER.info("\nport: {}\nusername: {}\npassword: {}",
                jsonObject.getString("port"),
                jsonObject.getString("username"),
                jsonObject.getString("password"));
        LOGGER.info("==============server info================");
    }
    /**
     * 部署失败
     *
     * @param throwable Exception信息
     */
    private void deployVerticalFailed(Throwable throwable) {
        LOGGER.error(throwable.getClass().getName() + ": " + throwable.getMessage());
        System.exit(-1);
    }

    /**
     * 启动时间信息
     *
     * @param compositeFuture future wraps a list
     */
    private void deployWorkVerticalSuccess(CompositeFuture compositeFuture) {
        var t1 = ((double) (System.currentTimeMillis() - startTime)) / 1000;
        var t2 = ((double) System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000;
        LOGGER.info("web服务启动成功 -> 用时: {}s, jvm启动用时: {}s", t1, t2);

        // 检查是否处于安装引导模式（数据库未配置）
        Object installMode = VertxHolder.getVertxInstance().sharedData()
                .getLocalMap(LOCAL).get("installMode");
        if (Boolean.TRUE.equals(installMode)) {
            LOGGER.info("系统处于安装引导模式，等待用户完成数据库配置后再启动后置初始化...");
            return;
        }

        // 正常模式：部署 PostExecVerticle 执行 AppRun 实现
        deployPostExec();
    }

    /**
     * 部署 PostExecVerticle（执行所有 AppRun 实现）
     * 安装引导完成后也可手动调用此方法触发后置初始化
     */
    public void deployPostExec() {
        var vertx = VertxHolder.getVertxInstance();
        var postExecFuture = vertx.deployVerticle(PostExecVerticle.class, getWorkDeploymentOptions("postExec", 2));
        postExecFuture.onSuccess(id -> {
            LOGGER.info("PostExecVerticle 部署成功，AppRun 实现执行完成");
        }).onFailure(e -> {
            LOGGER.error("PostExecVerticle 部署失败", e);
        });
    }

    /**
     * 重新部署 ServiceVerticle，重新注册因 DB 未就绪而失败的服务到 EventBus
     * 安装引导完成、DB 初始化后调用
     */
    public void redeployServices() {
        var vertx = VertxHolder.getVertxInstance();
        vertx.deployVerticle(ServiceVerticle.class, getWorkDeploymentOptions("Service"))
                .onSuccess(id -> LOGGER.info("ServiceVerticle 重新部署成功，DB 相关服务已注册"))
                .onFailure(e -> LOGGER.error("ServiceVerticle 重新部署失败", e));
    }

    /**
     * deploy Verticle Options
     *
     * @param name the worker pool name
     * @return Deployment Options
     */
    private DeploymentOptions getWorkDeploymentOptions(String name) {
        return getWorkDeploymentOptions(name, customConfig.getInteger(ASYNC_SERVICE_INSTANCES));
    }

    private DeploymentOptions getWorkDeploymentOptions(String name, int ins) {
        return new DeploymentOptions()
                .setWorkerPoolName(name)
                .setThreadingModel(ThreadingModel.WORKER)
                .setInstances(ins);
    }

}
