package cn.qaiu.vx.core.handlerfactory;

import cn.qaiu.vx.core.annotaions.DateFormat;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.annotaions.SockRouteMapper;
import cn.qaiu.vx.core.base.BaseHttpApi;
import cn.qaiu.vx.core.interceptor.BeforeInterceptor;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.*;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import javassist.CtClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.qaiu.vx.core.util.ConfigConstant.ROUTE_TIME_OUT;
import static cn.qaiu.vx.core.verticle.ReverseProxyVerticle.REROUTE_PATH_PREFIX;
import static io.vertx.core.http.HttpHeaders.*;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

/**
 * 路由映射, 参数绑定
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class RouterHandlerFactory implements BaseHttpApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(RouterHandlerFactory.class);

    private static final Set<HttpMethod> httpMethods = new HashSet<>() {{
        add(HttpMethod.GET);
        add(HttpMethod.POST);
        add(HttpMethod.OPTIONS);
        add(HttpMethod.PUT);
        add(HttpMethod.DELETE);
        add(HttpMethod.HEAD);
    }};

    private final String gatewayPrefix;

    public RouterHandlerFactory(String gatewayPrefix) {
        Objects.requireNonNull(gatewayPrefix, "The gateway prefix is empty.");
        this.gatewayPrefix = gatewayPrefix;
    }

    /**
     * 开始扫描并注册handler
     */
    public Router createRouter() {
        // 主路由
        Router mainRouter = Router.router(VertxHolder.getVertxInstance());
        mainRouter.route().handler(ctx -> {
            String realPath = ctx.request().uri();;
            if (realPath.startsWith(REROUTE_PATH_PREFIX)) {
                // vertx web proxy暂不支持rewrite, 所以这里进行手动替换, 请求地址中的请求path前缀替换为originPath
                String rePath = realPath.substring(REROUTE_PATH_PREFIX.length());
                ctx.reroute(rePath);
                return;
            }

            LOGGER.debug("The HTTP service request address information ===>path:{}, uri:{}, method:{}",
                    ctx.request().path(), ctx.request().absoluteURI(), ctx.request().method());
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.response().headers().add(DATE, LocalDateTime.now().format(ISO_LOCAL_DATE_TIME));
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS, PUT, DELETE, HEAD");
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_HEADERS, "X-PINGOTHER, Origin,Content-Type, Accept, " +
                    "X-Requested-With, Dev, Authorization, Version, Token");
            ctx.response().headers().add(ACCESS_CONTROL_MAX_AGE, "1728000");
            ctx.next();
        });
        // 添加跨域的方法
        mainRouter.route().handler(CorsHandler.create().addRelativeOrigin(".*").allowCredentials(true).allowedMethods(httpMethods));

        // 配置文件上传路径
        mainRouter.route().handler(BodyHandler.create().setUploadsDirectory("uploads"));

        // 配置Session管理 - 用于演练场登录状态持久化
        // 30天过期时间（毫秒）
        SessionStore sessionStore = LocalSessionStore.create(VertxHolder.getVertxInstance());
        SessionHandler sessionHandler = SessionHandler.create(sessionStore)
                .setSessionTimeout(30L * 24 * 60 * 60 * 1000) // 30天
                .setSessionCookieName("SESSIONID") // Cookie名称
                .setCookieHttpOnlyFlag(true) // 防止XSS攻击
                .setCookieSecureFlag(false); // 非HTTPS环境设置为false
        mainRouter.route().handler(sessionHandler);

        // 拦截器
        Set<Handler<RoutingContext>> interceptorSet = getInterceptorSet();
        Route route0 = mainRouter.route("/*");
        interceptorSet.forEach(route0::handler);

        try {
            Set<Class<?>> handlers = reflections.getTypesAnnotatedWith(RouteHandler.class);
            Comparator<Class<?>> comparator = (c1, c2) -> {
                RouteHandler routeHandler1 = c1.getAnnotation(RouteHandler.class);
                RouteHandler routeHandler2 = c2.getAnnotation(RouteHandler.class);
                return Integer.compare(routeHandler2.order(), routeHandler1.order());
            };
            // 获取处理器类列表
            List<Class<?>> sortedHandlers = handlers.stream().sorted(comparator).toList();
            for (Class<?> handler : sortedHandlers) {
                try {
                    // 注册请求处理方法
                    registerNewHandler(mainRouter, handler);
                } catch (Throwable e) {
                    LOGGER.error("Error register {}, Error details：", handler, e.getCause());

                }
            }
        } catch (Exception e) {
            LOGGER.error("Manually Register Handler Fail, Error details：" + e.getMessage());
        }
        // 错误请求处理
        mainRouter.errorHandler(405, ctx -> doFireJsonResultResponse(ctx, JsonResult
                .error("Method Not Allowed", 405)));
        mainRouter.errorHandler(404, ctx -> ctx.response().setStatusCode(404).setChunked(true)
                .end("Internal server error: 404 not found"));

        return mainRouter;
    }

    /**
     * 注册handler
     */
    private void registerNewHandler(Router router, Class<?> handler) throws Throwable {
        String root = getRootPath(handler);
        Object instance = ReflectionUtil.newWithNoParam(handler);
        Method[] methods = handler.getMethods();
        // 注册处理方法排序
        Comparator<Method> comparator = (m1, m2) -> {
            RouteMapping mapping1 = m1.getAnnotation(RouteMapping.class);
            RouteMapping mapping2 = m2.getAnnotation(RouteMapping.class);
            return Integer.compare(mapping2.order(), mapping1.order());
        };
        List<Method> methodList = Stream.of(methods).filter(
                method -> method.isAnnotationPresent(RouteMapping.class)
        ).sorted(comparator).collect(Collectors.toList());

        methodList.addAll(Stream.of(methods).filter(
                method -> method.isAnnotationPresent(SockRouteMapper.class)
        ).toList());

        // 依次注册处理方法
        for (Method method : methodList) {
            if (method.isAnnotationPresent(RouteMapping.class)) {
                // 普通路由
                RouteMapping mapping = method.getAnnotation(RouteMapping.class);
                HttpMethod routeMethod = HttpMethod.valueOf(mapping.method().name());
                String routeUrl = getRouteUrl(mapping.value());
                String url = root.concat(routeUrl);
                // 匹配方法
                Route route = router.route(routeMethod, url);
                String mineType = mapping.requestMIMEType().getValue();
                LOGGER.info("route -> {}:{} -> {}", routeMethod.name(), url, mineType);
                if (StringUtils.isNotEmpty(mineType)) {
                    route.consumes(mineType);
                }

                // 设置默认超时
                route.handler(TimeoutHandler.create(SharedDataUtil.getCustomConfig().getInteger(ROUTE_TIME_OUT)));
                route.handler(ResponseTimeHandler.create());
                route.handler(ctx -> handlerMethod(instance, method, ctx)).failureHandler(ctx -> {
                    if (ctx.response().ended()) return;
                    // 超时处理器状态码503
                    if (ctx.statusCode() == 503 || ctx.failure() == null) {
                        doFireJsonResultResponse(ctx, JsonResult.error("未知异常, 请联系管理员", 500));
                    } else {
                        ctx.failure().printStackTrace();
                        doFireJsonResultResponse(ctx, JsonResult.error(ctx.failure().getMessage(), 500));
                    }
                });
            } else if (method.isAnnotationPresent(SockRouteMapper.class)) {
                // websocket 基于sockJs
                SockRouteMapper mapping = method.getAnnotation(SockRouteMapper.class);
                String routeUrl = getRouteUrl(mapping.value());
                String url = root.concat(routeUrl);
                LOGGER.info("Register New Websocket Handler -> {}", url);
                SockJSHandlerOptions options = new SockJSHandlerOptions()
                        .setHeartbeatInterval(2000)
                        .setRegisterWriteHandler(true);

                SockJSHandler sockJSHandler = SockJSHandler.create(VertxHolder.getVertxInstance(), options);
                Router route = sockJSHandler.socketHandler(sock -> {
                    try {
                        ReflectionUtil.invokeWithArguments(method, instance, sock);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
                if (url.endsWith("*")) {
                    throw new IllegalArgumentException("Don't include * when mounting a sub router");
                }
                router.route(url + "*").subRouter(route);
            }
        }
    }


    /**
     * 获取并处理路由URL分隔符
     *
     * @return String
     */
    private String getRouteUrl(String mapperValue) {
        String routeUrl;
        if ("/".equals(mapperValue)) {
            routeUrl = mapperValue;
        } else if (mapperValue.startsWith("/")) {
            routeUrl = mapperValue.substring(1);
        } else {
            routeUrl = mapperValue;
        }
        return routeUrl;
    }

    /**
     * 配置拦截
     *
     * @return Handler
     */
    private Set<Handler<RoutingContext>> getInterceptorSet() {
        // 配置拦截
        return getBeforeInterceptor().stream().map(BeforeInterceptor::doHandle).collect(Collectors.toSet());
    }

    /**
     * 获取请求根路径
     *
     * @param handler handler
     * @return 根路径
     */
    private String getRootPath(Class<?> handler) {
        // 处理请求路径前缀和后缀
        String root = gatewayPrefix;
        if (!root.startsWith("/")) {
            root = "/" + root;
        }
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        // 子路径
        if (handler.isAnnotationPresent(RouteHandler.class)) {
            RouteHandler routeHandler = handler.getAnnotation(RouteHandler.class);
            String value = routeHandler.value();
            root += (value.startsWith("/") ? value.substring(1) : value);
        }
        if (!root.endsWith("/")) {
            root = root + "/";
        }

        return root;
    }

    /**
     * 处理请求-参数绑定
     *
     * @param instance 类实例
     * @param method   处理方法
     * @param ctx      路由上下文
     */
    private void handlerMethod(Object instance, Method method, RoutingContext ctx) {
        // 方法参数名列表
        Map<String, Pair<Annotation[], CtClass>> methodParameters = ReflectionUtil.getMethodParameter(method);
        Map<String, Pair<Annotation[], CtClass>> methodParametersTemp = new LinkedHashMap<>(methodParameters);
        Map<String, String> pathParamValues = ctx.pathParams();

        // 参数名-参数值
        Map<String, Object> parameterValueList = new LinkedHashMap<>();
        methodParameters.forEach((k, v) -> parameterValueList.put(k, null));

        //绑定rest路径变量
        if (!pathParamValues.isEmpty()) {
            methodParameters.forEach((k, v) -> {
                if (pathParamValues.containsKey(k)) {
                    methodParametersTemp.remove(k);
                    if (ReflectionUtil.isBasicType(v.getRight())) {
                        String fmt = getFmt(v.getLeft(), v.getRight());
                        parameterValueList.put(k, ReflectionUtil.conversion(v.getRight(), pathParamValues.get(k), fmt));
                    } else if (ReflectionUtil.isBasicTypeArray(v.getRight())) {
                        parameterValueList.put(k, ReflectionUtil.conversionArray(v.getRight(), pathParamValues.get(k)));
                    } else {
                        throw new RuntimeException("参数绑定异常: 类型不匹配");
                    }
                }
            });
        }

        JsonArray entityPackagesReg = SharedDataUtil.getJsonArrayForCustomConfig("entityPackagesReg");

        final MultiMap queryParams = ctx.queryParams();
        // 解析body-json参数
        // 只处理POST/PUT/PATCH等有body的请求方法，避免GET请求读取body导致"Request has already been read"错误
        String httpMethod = ctx.request().method().name();
        if (("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod))
                && HttpHeaderValues.APPLICATION_JSON.toString().equals(ctx.parsedHeaders().contentType().value())
                && ctx.body() != null && ctx.body().asJsonObject() != null) {
            JsonObject body = ctx.body().asJsonObject();
            if (body != null) {
                methodParametersTemp.forEach((k, v) -> {
                    // 只解析已配置包名前缀的实体类
                    if (CommonUtil.matchRegList(entityPackagesReg.getList(), v.getRight().getName())) {
                        try {
                            Class<?> aClass = Class.forName(v.getRight().getName());
                            JsonObject data = CommonUtil.getSubJsonForEntity(body, aClass);
                            if (!data.isEmpty()) {
                                Object entity = data.mapTo(aClass);
                                parameterValueList.put(k, entity);
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        } else if (("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod))
                && ctx.body() != null) {
            queryParams.addAll(ParamUtil.paramsToMap(ctx.body().asString()));
        }

        // 解析其他参数
        if ("POST".equals(ctx.request().method().name())) {
            queryParams.addAll(ctx.request().params());
        }
        // 绑定get或post请求头的请求参数
        methodParametersTemp.forEach((k, v) -> {
            if (ReflectionUtil.isBasicType(v.getRight())) {
                String fmt = getFmt(v.getLeft(), v.getRight());
                String value = queryParams.get(k);
                parameterValueList.put(k, ReflectionUtil.conversion(v.getRight(), value, fmt));
            } else if (RoutingContext.class.getName().equals(v.getRight().getName())) {
                parameterValueList.put(k, ctx);
            } else if (HttpServerRequest.class.getName().equals(v.getRight().getName())) {
                parameterValueList.put(k, ctx.request());
            } else if (HttpServerResponse.class.getName().equals(v.getRight().getName())) {
                parameterValueList.put(k, ctx.response());
            } else if (parameterValueList.get(k) == null
                    && CommonUtil.matchRegList(entityPackagesReg.getList(), v.getRight().getName())) {
                // 绑定实体类
                try {
                    Class<?> aClass = Class.forName(v.getRight().getName());
                    Object entity = ParamUtil.multiMapToEntity(queryParams, aClass);
                    parameterValueList.put(k, entity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        // 调用handle 获取响应对象
        Object[] parameterValueArray = parameterValueList.values().toArray(new Object[0]);
        try {
            // 反射调用
            Object data = ReflectionUtil.invokeWithArguments(method, instance, parameterValueArray);
            if (data != null) {

                if (data instanceof JsonResult) {
                    doFireJsonResultResponse(ctx, (JsonResult<?>) data);
                }
                if (data instanceof JsonObject) {
                    doFireJsonObjectResponse(ctx, ((JsonObject) data));
                } else if (data instanceof Future) { // 处理异步响应
                    ((Future<?>) data).onSuccess(res -> {
                        if (res instanceof JsonResult) {
                            doFireJsonResultResponse(ctx, (JsonResult<?>) res);
                        }
                        if (res instanceof JsonObject) {
                            doFireJsonObjectResponse(ctx, ((JsonObject) res));
                        } else if (res != null) {
                            doFireJsonResultResponse(ctx, JsonResult.data(res));
                        } else {
                            handleAfterInterceptor(ctx, null);
                        }

                    }).onFailure(e -> doFireJsonResultResponse(ctx, JsonResult.error(e.getMessage())));
                } else {
                    doFireJsonResultResponse(ctx, JsonResult.data(data));
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            String err = e.getMessage();
            if (e.getCause() != null) {
                if (e.getCause() instanceof InvocationTargetException) {
                    err = ((InvocationTargetException) e.getCause()).getTargetException().getMessage();
                } else {
                    err = e.getCause().getMessage();
                }
            }
            doFireJsonResultResponse(ctx, JsonResult.error(err));
        }
    }

    /**
     * 获取DateFormat注解值
     */
    private String getFmt(Annotation[] parameterAnnotations, CtClass v) {
        String fmt = "";
        if (Date.class.getName().equals(v.getName())) {
            for (Annotation annotation : parameterAnnotations) {
                if (annotation instanceof DateFormat) {
                    fmt = ((DateFormat) annotation).value();
                }
            }
        }
        return fmt;
    }

    private Set<BeforeInterceptor> getBeforeInterceptor() {
        Set<Class<? extends BeforeInterceptor>> interceptorClassSet =
                reflections.getSubTypesOf(BeforeInterceptor.class);
        if (interceptorClassSet == null) {
            return new HashSet<>();
        }
        return CommonUtil.sortClassSet(interceptorClassSet);
    }
}
