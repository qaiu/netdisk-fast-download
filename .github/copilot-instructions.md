# GitHub Copilot Instructions - NetDisk Fast Download

## 项目简介
网盘快速下载项目，支持多种网盘链接解析和下载加速的 Java Web 应用。

## 技术栈要求

### 核心技术
- **Java**: JDK 17（必须）
- **框架**: Vert.x 4.5.23（异步响应式框架）
- **构建**: Maven 3.x
- **日志**: SLF4J 2.0.5 + Logback 1.5.19
- **前端**: Vue.js + Monaco Editor

### 重要依赖
- Lombok 1.18.38 - 简化 Java 代码
- Jackson 2.14.2 - JSON 处理
- Commons Lang3 3.18.0 - 工具类
- Reflections 0.10.2 - 反射工具

## 代码生成规范

### Java 代码风格

#### 1. 使用 Lombok 简化代码
```java
// ✅ 推荐：使用 Lombok 注解
@Data
@Builder
@Slf4j
public class Example {
    private String name;
    private int value;
}

// ❌ 避免：手写 getter/setter
public class Example {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

#### 2. 异步编程模式（Vert.x）
```java
// ✅ 推荐：使用 Vert.x Future
public Future<String> fetchData() {
    return vertx.createHttpClient()
        .request(HttpMethod.GET, "http://example.com")
        .compose(HttpClientRequest::send)
        .compose(response -> response.body())
        .map(Buffer::toString);
}

// ❌ 避免：阻塞操作
public String fetchData() {
    // 不要在 Event Loop 中执行阻塞代码
    Thread.sleep(1000); // ❌
    return result;
}
```

#### 3. 日志记录
```java
// ✅ 推荐：使用 @Slf4j + 参数化日志
@Slf4j
public class Service {
    public void process(String id) {
        log.info("Processing item: {}", id);
        try {
            // ...
        } catch (Exception e) {
            log.error("Failed to process item: {}", id, e);
        }
    }
}

// ❌ 避免：字符串拼接
log.info("Processing item: " + id); // 性能差
System.out.println("Debug info"); // 不使用 System.out
```

#### 4. 异常处理
```java
// ✅ 推荐：完整的异常处理
public Future<Result> operation() {
    return service.execute()
        .recover(err -> {
            log.error("Operation failed", err);
            return Future.succeededFuture(Result.error(err.getMessage()));
        });
}

// ❌ 避免：空的 catch 块或吞掉异常
try {
    doSomething();
} catch (Exception e) {
    // ❌ 空 catch
}
```

### 包和类命名

- 基础包名：`cn.qaiu`
- 模块包结构：
  - `cn.qaiu.core.*` - 核心功能
  - `cn.qaiu.parser.*` - 解析器相关
  - `cn.qaiu.db.*` - 数据库相关
  - `cn.qaiu.service.*` - 业务服务
  - `cn.qaiu.web.*` - Web 相关

### 测试代码

```java
// ✅ 推荐：JUnit 4 测试
public class ServiceTest {
    
    @Before
    public void setUp() {
        // 初始化
    }
    
    @Test
    public void testMethod() {
        // Given
        String input = "test";
        
        // When
        String result = service.process(input);
        
        // Then
        assertEquals("expected", result);
    }
    
    @After
    public void tearDown() {
        // 清理
    }
}
```

## 特定模块指导

### Core 模块 - Web 路由封装（必须使用，禁止重复造轮子）

**核心思想：使用注解定义路由，框架自动处理请求和响应**

#### 1. 使用 @RouteHandler 和 @RouteMapping
```java
// ✅ 推荐：使用注解定义路由
@RouteHandler(value = "/api/v1", order = 10)
@Slf4j
public class UserController {
    
    private final UserService userService = AsyncServiceUtil.getAsyncServiceInstance(UserService.class);
    
    // GET /api/v1/users
    @RouteMapping(value = "/users", method = RouteMethod.GET)
    public Future<JsonResult<List<User>>> getUsers() {
        return userService.findAll()
            .map(JsonResult::success)
            .otherwise(err -> JsonResult.error(err.getMessage()));
    }
    
    // GET /api/v1/user/:id （路径参数自动注入）
    @RouteMapping(value = "/user/:id", method = RouteMethod.GET)
    public Future<User> getUser(String id) {
        // 返回值自动序列化为 JSON
        return userService.findById(id);
    }
    
    // POST /api/v1/user （查询参数自动注入）
    @RouteMapping(value = "/user", method = RouteMethod.POST)
    public Future<JsonResult<User>> createUser(HttpServerRequest request, String name, Integer age) {
        return userService.create(name, age)
            .map(JsonResult::success);
    }
    
    // 重定向示例
    @RouteMapping(value = "/redirect/:id", method = RouteMethod.GET)
    public void redirect(HttpServerResponse response, String id) {
        String targetUrl = "https://example.com/" + id;
        ResponseUtil.redirect(response, targetUrl);
    }
}

// ❌ 避免：手动创建 Router 和 Handler
Router router = Router.router(vertx);
router.get("/api/users").handler(ctx -> {
    // 不要这样写！使用注解方式
});
```

#### 2. 自动参数注入规则
- **路径参数**：`/user/:id` → `public Future<User> getUser(String id)`
- **查询参数**：`?name=xxx&age=18` → `public Future<User> create(String name, Integer age)`
- **Vert.x 对象**：自动注入 `HttpServerRequest`, `HttpServerResponse`, `RoutingContext`
- **请求体**：POST/PUT 的 JSON 自动反序列化为方法参数对象

#### 3. 响应处理
```java
// 方式1：返回 Future，框架自动处理
public Future<User> getUser(String id) {
    return userService.findById(id);  // 自动序列化为 JSON
}

// 方式2：返回 JsonResult 统一格式
public Future<JsonResult<User>> getUser(String id) {
    return userService.findById(id).map(JsonResult::success);
}

// 方式3：手动控制响应（仅在特殊情况使用）
public void customResponse(HttpServerResponse response) {
    ResponseUtil.fireJsonObjectResponse(response, jsonObject);
}
```

#### 4. WebSocket 路由
```java
@RouteHandler("/ws")
public class WebSocketHandler {
    
    @SockRouteMapper("/chat")
    public void handleChat(SockJSSocket socket) {
        socket.handler(buffer -> {
            log.info("Received: {}", buffer.toString());
            socket.write(buffer);  // Echo
        });
    }
}
```

### Core-Database 模块 - DDL 自动生成（必须使用，禁止重复造轮子）

**核心思想：使用注解定义实体，自动生成建表 SQL**

#### 1. 定义实体类
```java
// ✅ 推荐：使用注解定义实体
@Data
@Table(value = "t_user", keyFields = "id")  // 表名和主键
public class User {
    
    @Constraint(autoIncrement = true)
    private Long id;  // 主键自增
    
    @Constraint(notNull = true, uniqueKey = "uk_email")
    @Length(varcharSize = 100)
    private String email;  // 非空 + 唯一索引 + 长度100
    
    @Constraint(notNull = true)
    @Length(varcharSize = 50)
    private String name;
    
    @Constraint(defaultValue = "0")
    private Integer status;  // 默认值 0
    
    @Constraint(defaultValue = "NOW()", defaultValueIsFunction = true)
    private Date createdAt;  // 默认当前时间
    
    @TableGenIgnore  // 忽略此字段，不生成列
    private transient String tempField;
}

// 应用启动时自动建表
CreateTable.createTable(pool, JDBCType.MySQL);

// ❌ 避免：手写建表 SQL
String sql = "CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, ...)";
pool.query(sql).execute();  // 不要这样写！
```

#### 2. 支持的注解

**@Table** - 表定义
- `value` - 表名（默认类名转下划线）
- `keyFields` - 主键字段名（默认 "id"）

**@Constraint** - 字段约束
- `notNull = true` - 非空约束
- `uniqueKey = "uk_name"` - 唯一索引（相同名称的字段组成联合唯一索引）
- `defaultValue = "value"` - 默认值
- `defaultValueIsFunction = true` - 默认值是函数（如 NOW()）
- `autoIncrement = true` - 自增（仅用于主键）

**@Length** - 字段长度
- `varcharSize = 255` - VARCHAR 长度（默认 255）
- `decimalSize = {10, 2}` - DECIMAL 精度（默认 {22, 2}）

**@Column** - 自定义列名
- `name = "column_name"` - 指定数据库列名

**@TableGenIgnore** - 忽略字段（不生成列）

#### 3. 自动创建数据库
```java
// ✅ 推荐：自动创建数据库
JsonObject dbConfig = new JsonObject()
    .put("jdbcUrl", "jdbc:mysql://localhost:3306/mydb")
    .put("username", "root")
    .put("password", "password");

CreateDatabase.createDatabase(dbConfig);

// ❌ 避免：手动连接和执行 CREATE DATABASE
```

#### 4. 支持的数据库类型
- `JDBCType.MySQL` - MySQL
- `JDBCType.PostgreSQL` - PostgreSQL
- `JDBCType.H2DB` - H2 数据库

### Parser 模块
- 支持自定义解析器（Java/Python/JavaScript）
- Python 使用 GraalPy 执行
- 需要考虑安全性和沙箱隔离
- WebSocket 支持外部 Python 环境连接

```java
// Parser 接口实现示例
public class CustomParser implements IParser {
    @Override
    public Future<ParseResult> parse(String url, Map<String, String> params) {
        return Future.future(promise -> {
            // 异步解析逻辑
            promise.complete(result);
        });
    }
}
```

## Maven 配置注意事项

### 测试执行
```bash
# 默认打包跳过测试
mvn clean package

# 执行测试
mvn test -Dmaven.test.skip=false
mvn clean package -Dmaven.test.skip=false
```

### 模块化构建
```bash
# 构建特定模块
mvn clean package -pl parser -am
```

## 重要约定

### 1. 异步优先
- 所有 I/O 操作必须异步
- 使用 Vert.x Future/Promise API
- 避免阻塞 Event Loop

### 2. 资源管理
```java
// ✅ 推荐：使用 try-with-resources
try (InputStream is = new FileInputStream(file)) {
    // 使用资源
}

// 或者确保在 finally 中关闭
HttpClient client = vertx.createHttpClient();
// 使用后必须关闭
client.close();
```

### 3. 配置外部化
- 配置文件优先使用 JSON 格式
- 敏感信息不要硬编码
- 支持环境变量覆盖

### 4. 错误处理
- 使用 Future 的 recover/otherwise
- 记录详细的错误日志
- 向用户返回友好的错误信息

## 性能考虑

1. **使用连接池**: 数据库连接、HTTP 客户端
2. **缓存策略**: 解析结果、静态资源
3. **批量操作**: 避免 N+1 查询问题
4. **异步非阻塞**: 充分利用 Vert.x 优势

## 安全要求

### Parser 模块安全
- 执行自定义代码必须沙箱隔离
- 限制资源访问（文件、网络）
- 设置执行超时
- 验证输入参数

```java
// ✅ 推荐：带安全检查的执行
public Future<Result> executeUserCode(String code) {
    // 验证代码
    if (!SecurityValidator.isValid(code)) {
        return Future.failedFuture("Invalid code");
    }
    
    // 在沙箱中执行
    return sandboxExecutor.execute(code, TIMEOUT);
}
```

### 输入验证
```java
// ✅ 推荐：验证所有外部输入
public Future<Result> parse(String url) {
    if (StringUtils.isBlank(url) || !UrlValidator.isValid(url)) {
        return Future.failedFuture("Invalid URL");
    }
    // 继续处理
}
```

## 文档和注释

### JavaDoc 注释
```java
/**
 * 解析网盘链接获取下载信息
 * 
 * @param url 网盘分享链接
 * @param params 额外参数（如密码）
 * @return Future<ParseResult> 解析结果
 */
public Future<ParseResult> parse(String url, Map<String, String> params) {
    // 实现
}
```

### 复杂逻辑注释
```java
// 处理特殊情况：某些网盘需要二次验证
// 参考文档：docs/parser-flow.md
if (needsSecondaryVerification) {
    // 实现二次验证逻辑
}
```

## 常见模式

### 链式异步调用
```java
return fetchMetadata(url)
    .compose(meta -> validateMetadata(meta))
    .compose(meta -> fetchDownloadUrl(meta))
    .compose(downloadUrl -> generateResult(downloadUrl))
    .recover(this::handleError);
```

### 事件处理
```java
vertx.eventBus().<JsonObject>consumer("parser.request", msg -> {
    JsonObject body = msg.body();
    parse(body.getString("url"))
        .onSuccess(result -> msg.reply(JsonObject.mapFrom(result)))
        .onFailure(err -> msg.fail(500, err.getMessage()));
});
```

## 不应该做的事

1. ❌ 在 Event Loop 线程中执行阻塞操作
2. ❌ 使用 `System.out.println()` 而不是日志框架
3. ❌ 硬编码配置值（端口、路径、密钥等）
4. ❌ 忽略异常或使用空 catch 块
5. ❌ 返回 null，应该使用 Optional 或 Future.failedFuture()
6. ❌ 在生产代码中使用 `e.printStackTrace()`
7. ❌ 直接操作 Thread 而不使用 Vert.x 的 executeBlocking
8. ❌ 提交包含 `logs/` 目录的代码

## 代码审查清单

生成代码时请确保：
- [ ] 使用 Lombok 注解简化代码
- [ ] 异步操作使用 Vert.x Future
- [ ] 添加了 @Slf4j 和适当的日志
- [ ] 异常处理完整
- [ ] 输入参数已验证
- [ ] 资源正确释放
- [ ] 添加了必要的 JavaDoc
- [ ] 遵循项目包命名规范
- [ ] 没有阻塞操作在 Event Loop 中
- [ ] 测试用例覆盖主要场景

## 参考资源

- Vert.x 文档: https://vertx.io/docs/
- 项目 Parser 文档: `parser/doc/`
- 前端文档: `web-front/doc/`
- 安全测试指南: `parser/doc/SECURITY_TESTING_GUIDE.md`
