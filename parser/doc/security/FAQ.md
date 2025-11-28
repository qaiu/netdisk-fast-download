# 安全修复常见问题 FAQ

## ❓ 常见问题解答

### Q1: 为什么还是显示"请求失败: 404"？

**答**: 这是**正常现象**！404是HTTP响应状态码，说明：

✅ **安全检查已通过** - 你的请求没有被SSRF防护拦截  
✅ **请求已发出** - HTTP客户端工作正常  
❌ **目标资源不存在** - 目标服务器返回404错误

#### 如何区分安全拦截 vs 正常404？

| 错误类型 | 错误消息 | 原因 |
|---------|---------|------|
| **安全拦截** | `SecurityException: 🔒 安全拦截: 禁止访问内网IP地址` | SSRF防护拦截 |
| **安全拦截** | `SecurityException: 🔒 安全拦截: 禁止访问云服务元数据API` | 危险域名拦截 |
| **正常404** | `Error: 请求失败: 404` | 目标URL不存在 |
| **正常错误** | `HTTP请求超时` | 网络超时 |
| **正常错误** | `Connection refused` | 目标服务器拒绝连接 |

#### 示例对比

**❌ 被安全拦截（内网攻击）**:
```javascript
try {
    var response = http.get('http://127.0.0.1:6400/admin');
} catch (e) {
    // 错误消息: SecurityException: 🔒 安全拦截: 禁止访问内网IP地址
    logger.error(e.message);  
}
```

**✅ 正常404（资源不存在）**:
```javascript
try {
    var response = http.get('https://httpbin.org/not-exist');
    if (response.statusCode() !== 200) {
        // 404是正常的HTTP响应，不是安全拦截
        throw new Error("请求失败: " + response.statusCode());
    }
} catch (e) {
    // 错误消息: Error: 请求失败: 404
    logger.error(e.message);
}
```

#### 解决方法

如果你的代码中有这样的检查：

```javascript
// ❌ 不好的做法：对所有非200状态码都抛出异常
if (response.statusCode() !== 200) {
    throw new Error("请求失败: " + response.statusCode());
}
```

建议改为：

```javascript
// ✅ 更好的做法：区分不同的状态码
var statusCode = response.statusCode();

if (statusCode === 404) {
    logger.warn("资源不存在: " + url);
    return null;  // 或者其他默认值
}

if (statusCode < 200 || statusCode >= 300) {
    throw new Error("请求失败: " + statusCode);
}

return response.body();
```

---

### Q2: 如何确认安全修复已生效？

**答**: 执行以下测试：

```javascript
// 测试1: 尝试访问内网（应该被拦截）
try {
    http.get('http://127.0.0.1:6400/');
    logger.error('❌ 失败: 内网访问成功（不应该）');
} catch (e) {
    if (e.message.includes('安全拦截')) {
        logger.info('✅ 通过: 内网访问被拦截');
    } else {
        logger.warn('⚠️ 警告: 错误但非安全拦截 - ' + e.message);
    }
}

// 测试2: 访问外网（应该正常工作，可能返回404但不会被拦截）
try {
    var response = http.get('https://httpbin.org/status/200');
    logger.info('✅ 通过: 外网访问正常');
} catch (e) {
    logger.error('❌ 失败: 外网访问被拦截（不应该） - ' + e.message);
}
```

---

### Q3: Java.type() 相关错误

**错误消息**: `ReferenceError: "Java" is not defined`

**答**: 这是**正确的行为**！说明安全修复生效了。

之前（不安全）:
```javascript
var System = Java.type('java.lang.System');  // ❌ 可以执行
```

现在（安全）:
```javascript
var System = Java.type('java.lang.System');  // ✅ 抛出错误
// ReferenceError: "Java" is not defined
```

---

### Q4: 如何测试SSRF防护？

**答**: 使用以下测试用例：

```javascript
function testSSRF() {
    var tests = [
        // 应该被拦截的
        {url: 'http://127.0.0.1:6400/', shouldBlock: true},
        {url: 'http://localhost/', shouldBlock: true},
        {url: 'http://192.168.1.1/', shouldBlock: true},
        {url: 'http://169.254.169.254/latest/meta-data/', shouldBlock: true},
        
        // 应该允许的
        {url: 'https://httpbin.org/get', shouldBlock: false},
        {url: 'https://www.example.com/', shouldBlock: false}
    ];
    
    tests.forEach(function(test) {
        try {
            var response = http.get(test.url);
            if (test.shouldBlock) {
                logger.error('❌ 失败: ' + test.url + ' 应该被拦截但没有');
            } else {
                logger.info('✅ 通过: ' + test.url + ' 正确允许');
            }
        } catch (e) {
            if (test.shouldBlock && e.message.includes('安全拦截')) {
                logger.info('✅ 通过: ' + test.url + ' 正确拦截');
            } else if (!test.shouldBlock) {
                logger.error('❌ 失败: ' + test.url + ' 不应该被拦截 - ' + e.message);
            }
        }
    });
}
```

---

### Q5: 服务启动时出现 ArrayIndexOutOfBoundsException

**答**: 说明代码未更新或未重新编译。

**解决方法**:
```bash
# 1. 确认代码已更新
grep -n "new String\[0\]" parser/src/main/java/cn/qaiu/parser/customjs/JsPlaygroundExecutor.java

# 应该看到类似：
# 68: ScriptEngine engine = factory.getScriptEngine(new String[0], null, new SecurityClassFilter());

# 2. 重新编译
mvn clean install

# 3. 重启服务
./bin/stop.sh && ./bin/run.sh
```

---

### Q6: 如何关闭SSRF防护？（不推荐）

**⚠️ 警告**: 关闭SSRF防护会带来严重的安全风险！

如果确实需要（仅用于开发环境），可以修改 `JsHttpClient.java`:

```java
private void validateUrlSecurity(String url) {
    // 注释掉所有验证逻辑
    log.debug("SSRF防护已禁用（仅开发环境）");
    return;
}
```

**强烈建议**: 保持SSRF防护开启，使用白名单策略代替完全关闭。

---

### Q7: 如何添加域名白名单？

**答**: 当前策略是黑名单模式。如需白名单，修改 `validateUrlSecurity`:

```java
private static final String[] ALLOWED_DOMAINS = {
    "api.example.com",
    "cdn.example.com"
};

private void validateUrlSecurity(String url) {
    URI uri = new URI(url);
    String host = uri.getHost();
    
    // 白名单检查
    boolean allowed = false;
    for (String domain : ALLOWED_DOMAINS) {
        if (host.equals(domain) || host.endsWith("." + domain)) {
            allowed = true;
            break;
        }
    }
    
    if (!allowed) {
        throw new SecurityException("域名不在白名单中: " + host);
    }
}
```

---

### Q8: 性能影响

**Q**: 安全检查会影响性能吗？

**A**: 影响很小：
- ClassFilter: 在引擎初始化时执行一次，几乎无性能影响
- SSRF检查: 每次HTTP请求前执行，主要是DNS解析（已有缓存）
- 预计性能影响: < 5ms/请求

---

### Q9: 如何查看安全日志？

**答**:
```bash
# 查看安全拦截日志
tail -f logs/*/run.log | grep "安全拦截"

# 查看JavaScript引擎初始化日志
tail -f logs/*/run.log | grep "JavaScript引擎"

# 应该看到：
# 🔒 安全的JavaScript引擎初始化成功（演练场）
```

---

### Q10: 迁移到GraalVM

**Q**: 如何迁移到更安全的GraalVM JavaScript？

**A**: 

1. 添加依赖（`pom.xml`）:
```xml
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>js</artifactId>
    <version>23.0.0</version>
</dependency>
```

2. 修改代码:
```java
import org.graalvm.polyglot.*;

Context context = Context.newBuilder("js")
    .allowHostAccess(HostAccess.NONE)  // 禁止访问Java
    .allowIO(IOAccess.NONE)            // 禁止IO
    .build();

Value result = context.eval("js", jsCode);
```

GraalVM优势:
- ✅ 默认沙箱隔离
- ✅ 更好的安全性
- ✅ 更好的性能
- ✅ 活跃维护

---

## 📞 获取帮助

如果以上FAQ没有解决你的问题：

1. 查看详细文档: `parser/doc/security/`
2. 运行安全测试: `./parser/doc/security/test-security.sh`
3. 查看测试指南: `SECURITY_TESTING_GUIDE.md`

---

**最后更新**: 2025-11-29

