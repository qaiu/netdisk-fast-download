package cn.qaiu.vx.core.handlerfactory;

import cn.qaiu.vx.core.annotaions.DateFormat;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.annotaions.SockRouteMapper;
import cn.qaiu.vx.core.base.BaseHttpApi;
import cn.qaiu.vx.core.enums.MIMEType;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.*;
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
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import javassist.CtClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.core.http.HttpHeaders.*;

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
    // 需要扫描注册的Router路径
    private static volatile Reflections reflections;

    private final String gatewayPrefix;

    public RouterHandlerFactory(String routerScanAddress, String gatewayPrefix) {
        Objects.requireNonNull(routerScanAddress, "The router package address scan is empty.");
        Objects.requireNonNull(gatewayPrefix, "The gateway prefix is empty.");
        reflections = ReflectionUtil.getReflections(routerScanAddress);
        this.gatewayPrefix = gatewayPrefix;
    }

    /**
     * 开始扫描并注册handler
     */
    public Router createRouter() {
        Router router = Router.router(VertxHolder.getVertxInstance());

        // 静态资源
        String path = SharedDataUtil.getJsonConfig("server")
                .getString("staticResourcePath");
        if (!StringUtils.isEmpty(path)) {
            // 静态资源
            router.route("/*").handler(StaticHandler
                    .create(path)
                    .setCachingEnabled(true)
                    .setDefaultContentEncoding("UTF-8"));
        }


        router.route().handler(ctx -> {
            LOGGER.debug("The HTTP service request address information ===>path:{}, uri:{}, method:{}",
                    ctx.request().path(), ctx.request().absoluteURI(), ctx.request().method());
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS, PUT, DELETE, HEAD");
            ctx.response().headers().add(ACCESS_CONTROL_ALLOW_HEADERS, "X-PINGOTHER, Origin,Content-Type, Accept, " +
                    "X-Requested-With, Dev, Authorization, Version, Token");
            ctx.response().headers().add(ACCESS_CONTROL_MAX_AGE, "1728000");
            ctx.next();
        });
        // 添加跨域的方法
        router.route().handler(CorsHandler.create().addRelativeOrigin(".*").allowCredentials(true).allowedMethods(httpMethods));

        // 配置文件上传路径
        router.route().handler(BodyHandler.create().setUploadsDirectory("uploads"));


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
                    registerNewHandler(router, handler);
                } catch (Throwable e) {
                    LOGGER.error("Error register {}, Error details：", handler, e.getCause());

                }
            }
        } catch (Exception e) {
            LOGGER.error("Manually Register Handler Fail, Error details：" + e.getMessage());
        }
        // 错误请求处理
        router.errorHandler(405, ctx -> fireJsonResponse(ctx, JsonResult
                .error("Method Not Allowed", 405)));
        router.errorHandler(404, ctx -> ctx.response().setStatusCode(404).setChunked(true)
                .end("Internal server error: 404 not found"));

        return router;
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

        // 拦截器
        Handler<RoutingContext> interceptor = getInterceptor();
        // 依次注册处理方法
        for (Method method : methodList) {
            if (method.isAnnotationPresent(RouteMapping.class)) {
                // 普通路由
                RouteMapping mapping = method.getAnnotation(RouteMapping.class);
                HttpMethod routeMethod = HttpMethod.valueOf(mapping.method().name());
                String routeUrl = getRouteUrl(method.getName(), mapping.value());
                String url = root.concat(routeUrl);
                // 匹配方法
                Route route = router.route(routeMethod, url);
                String mineType = mapping.requestMIMEType().getValue();
                LOGGER.info("route -> {}:{} -> {}", routeMethod.name(), url, mineType);
                if (StringUtils.isNotEmpty(mineType)) {
                    route.consumes(mineType);
                }

                // 先执行拦截方法, 再进入业务请求
                route.handler(interceptor);
                route.handler(ctx -> handlerMethod(instance, method, ctx)).failureHandler(ctx -> {
                    if (ctx.response().ended()) return;
                    ctx.failure().printStackTrace();
                    fireJsonResponse(ctx, JsonResult.error(ctx.failure().getMessage(), 500));
                });
            } else if (method.isAnnotationPresent(SockRouteMapper.class)) {
                // websocket 基于sockJs
                SockRouteMapper mapping = method.getAnnotation(SockRouteMapper.class);
                String routeUrl = getRouteUrl(method.getName(), mapping.value());
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
     * @param methodName 路由method
     * @return String
     */
    private String getRouteUrl(String methodName, String mapperValue) {
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
     * @throws Throwable Throwable
     */
    private Handler<RoutingContext> getInterceptor() throws Throwable {
        // 配置拦截
        Class<?> interceptorClass = Class.forName(SharedDataUtil.getValueForCustomConfig("interceptorClassPath"));
        Object handleInstance = ReflectionUtil.newWithNoParam(interceptorClass);
        Method doHandle = interceptorClass.getMethod("doHandle");
        // 反射调用
        return CastUtil.cast(ReflectionUtil.invoke(doHandle, handleInstance));
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
            root += ("/".equals(value) ? "" : value);
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

        final MultiMap queryParams = ctx.queryParams();
        if ("POST".equals(ctx.request().method().name())) {
            queryParams.addAll(ctx.request().params());
        }

        JsonArray entityPackagesReg = SharedDataUtil.getJsonArrayForCustomConfig("entityPackagesReg");
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
            } else if (CommonUtil.matchRegList(entityPackagesReg.getList(), v.getRight().getName())) {
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
        // 解析body-json参数
        if ("application/json".equals(ctx.parsedHeaders().contentType().value()) && ctx.body().asJsonObject() != null) {
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
        }
        // 调用handle 获取响应对象
        Object[] parameterValueArray = parameterValueList.values().toArray(new Object[0]);
        try {
            // 反射调用
            Object data = ReflectionUtil.invokeWithArguments(method, instance, parameterValueArray);
            if (data != null) {
                if (data instanceof JsonResult) {
                    fireJsonResponse(ctx, data);
                } else if (data instanceof Future) { // 处理异步响应
                    ((Future<?>) data).onSuccess(res -> {
                        if (res instanceof JsonObject) {
                            fireJsonResponse(ctx, res);
                        } else if (res != null) {
                            fireJsonResponse(ctx, JsonResult.data(res));
                        }
                    }).onFailure(e -> fireJsonResponse(ctx, JsonResult.error(e.getMessage())));
                } else {
                    ctx.response().headers().set(CONTENT_TYPE, MIMEType.TEXT_HTML.getValue());
                    ctx.end(data.toString());
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
            fireJsonResponse(ctx, JsonResult.error(err));
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
}
