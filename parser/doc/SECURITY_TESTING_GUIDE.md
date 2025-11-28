# JavaScript执行器安全测试指南

## 概述

本文档提供了一套完整的安全测试用例，用于验证JavaScript演练场执行器的安全性。这些测试旨在检测潜在的安全漏洞，包括但不限于：

- 系统命令执行
- 文件系统访问
- 反射攻击
- 网络攻击 (SSRF)
- JVM退出
- DOS攻击
- 内存溢出

## ⚠️ 重要警告

**这些测试用例包含危险代码，仅用于安全测试目的！**

- ❌ 不要在生产环境执行这些测试
- ❌ 不要将这些代码暴露给未授权用户
- ✅ 仅在隔离的测试环境中执行
- ✅ 执行前确保有完整的系统备份

## 测试方式

### 方式1: JUnit单元测试

使用提供的JUnit测试类 `SecurityTest.java`：

```bash
cd parser
mvn test -Dtest=SecurityTest
```

### 方式2: HTTP接口测试

使用提供的HTTP测试文件 `playground-security-tests.http`：

1. 启动应用服务器
2. 在IDE中打开 `web-service/src/test/resources/playground-security-tests.http`
3. 逐个执行测试用例

或使用curl命令：

```bash
curl -X POST http://localhost:9000/v2/playground/test \
  -H "Content-Type: application/json" \
  -d @test-case.json
```

## 测试用例说明

### 1. 系统命令执行测试 🔴 高危

**测试目标**: 验证是否能通过Java的Runtime或ProcessBuilder执行系统命令

**危险级别**: ⚠️⚠️⚠️ 极高

**测试内容**:
- 尝试使用 `Runtime.getRuntime().exec()` 执行shell命令
- 尝试使用 `ProcessBuilder` 执行系统命令
- 尝试读取命令执行结果

**预期结果**: 
- ✅ **安全**: 无法访问 `Java.type()` 或相关类
- ❌ **危险**: 成功执行系统命令

**示例攻击**:
```javascript
var Runtime = Java.type('java.lang.Runtime');
var process = Runtime.getRuntime().exec('whoami');
```

---

### 2. 文件系统访问测试 🔴 高危

**测试目标**: 验证是否能读写本地文件系统

**危险级别**: ⚠️⚠️⚠️ 极高

**测试内容**:
- 尝试读取敏感文件 (`/etc/passwd`, 数据库文件等)
- 尝试写入文件到系统目录
- 尝试删除文件

**预期结果**:
- ✅ **安全**: 无法访问文件系统API
- ❌ **危险**: 成功读写文件

**示例攻击**:
```javascript
var Files = Java.type('java.nio.file.Files');
var content = Files.readAllLines(Paths.get('/etc/passwd'));
```

---

### 3. 系统属性访问测试 🟡 中危

**测试目标**: 验证是否能访问系统属性和环境变量

**危险级别**: ⚠️⚠️ 高

**测试内容**:
- 读取系统属性 (`user.home`, `user.name`, `java.version`)
- 读取环境变量 (`PATH`, `JAVA_HOME`, API密钥等)
- 修改系统属性

**预期结果**:
- ✅ **安全**: 无法访问System类
- ❌ **危险**: 成功获取敏感信息

**潜在风险**: 可能泄露系统配置、用户信息、API密钥等敏感数据

---

### 4. 反射攻击测试 🔴 高危

**测试目标**: 验证是否能通过反射绕过访问控制

**危险级别**: ⚠️⚠️⚠️ 极高

**测试内容**:
- 使用 `Class.forName()` 加载任意类
- 通过反射调用私有方法
- 修改final字段
- 获取ClassLoader

**预期结果**:
- ✅ **安全**: 无法使用反射API
- ❌ **危险**: 成功绕过访问控制

**示例攻击**:
```javascript
var Class = Java.type('java.lang.Class');
var systemClass = Class.forName('java.lang.System');
var methods = systemClass.getDeclaredMethods();
```

---

### 5. 网络Socket攻击测试 🔴 高危

**测试目标**: 验证是否能创建任意网络连接

**危险级别**: ⚠️⚠️⚠️ 极高

**测试内容**:
- 创建Socket连接到任意主机
- 使用URL/URLConnection访问任意地址
- 端口扫描

**预期结果**:
- ✅ **安全**: 无法创建网络连接
- ❌ **危险**: 可以连接任意主机端口

**潜在风险**: 可用于端口扫描、内网渗透、绕过防火墙

---

### 6. JVM退出攻击测试 🔴 高危

**测试目标**: 验证是否能终止JVM进程

**危险级别**: ⚠️⚠️⚠️ 极高

**测试内容**:
- 调用 `System.exit()`
- 调用 `Runtime.halt()`
- 触发致命错误

**预期结果**:
- ✅ **安全**: 无法退出JVM
- ❌ **危险**: 成功终止应用

**影响**: 导致整个应用崩溃，拒绝服务

---

### 7. HTTP客户端SSRF测试 🟡 中危

**测试目标**: 验证注入的httpClient是否可被滥用

**危险级别**: ⚠️⚠️ 高

**测试内容**:
- 访问内网地址 (127.0.0.1, 192.168.x.x, 10.x.x.x)
- 访问云服务元数据API (169.254.169.254)
- 访问本地服务端口
- 访问管理后台

**预期结果**:
- ✅ **最佳**: HTTP客户端有白名单限制
- ⚠️ **可接受**: 可以访问外网但不能访问内网
- ❌ **危险**: 可以访问任意地址包括内网

**潜在风险**: SSRF攻击、内网信息泄露、云服务凭证窃取

---

### 8. 对象滥用测试 🟡 中危

**测试目标**: 验证注入的Java对象是否可被反射访问

**危险级别**: ⚠️⚠️ 高

**测试内容**:
- 通过反射访问注入对象的私有字段
- 调用对象的非公开方法
- 修改对象内部状态

**预期结果**:
- ✅ **安全**: 无法通过反射访问对象
- ⚠️ **可接受**: 只能访问公开API
- ❌ **危险**: 可以访问和修改内部状态

---

### 9. DOS攻击测试 🟡 中危

**测试目标**: 验证是否存在执行时间限制

**危险级别**: ⚠️⚠️ 高

**测试内容**:
- 无限循环
- 长时间计算
- 递归调用

**预期结果**:
- ✅ **安全**: 有超时机制，自动中断执行
- ❌ **危险**: 可以无限执行

**影响**: 消耗CPU资源，导致服务响应缓慢或拒绝服务

---

### 10. 内存溢出测试 🟡 中危

**测试目标**: 验证是否存在内存使用限制

**危险级别**: ⚠️⚠️ 高

**测试内容**:
- 创建大量对象
- 分配大数组
- 递归创建深层对象

**预期结果**:
- ✅ **安全**: 有内存限制，防止OOM
- ❌ **危险**: 可以无限分配内存

**影响**: 导致内存溢出，应用崩溃

---

## 安全建议

### 当前Nashorn引擎的安全问题

Nashorn引擎默认允许JavaScript访问所有Java类，这是一个严重的安全隐患。以下是建议的安全措施：

### 1. 使用ClassFilter限制类访问 🔒 必须

```java
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

public class SecurityClassFilter implements ClassFilter {
    @Override
    public boolean exposeToScripts(String className) {
        // 黑名单：禁止访问危险类
        if (className.startsWith("java.lang.Runtime") ||
            className.startsWith("java.lang.ProcessBuilder") ||
            className.startsWith("java.io.File") ||
            className.startsWith("java.nio.file") ||
            className.startsWith("java.lang.System") ||
            className.startsWith("java.lang.Class") ||
            className.startsWith("java.lang.reflect") ||
            className.startsWith("java.net.Socket") ||
            className.startsWith("java.net.URL")) {
            return false;
        }
        
        // 白名单：只允许特定的类
        // return className.startsWith("允许的包名");
        
        return false; // 默认拒绝所有
    }
}

// 使用ClassFilter创建引擎
NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
ScriptEngine engine = factory.getScriptEngine(new SecurityClassFilter());
```

### 2. 设置执行超时 ⏱️ 强烈推荐

```java
// 使用Future + timeout
Future<?> future = executor.submit(() -> {
    engine.eval(jsCode);
});

try {
    future.get(30, TimeUnit.SECONDS); // 30秒超时
} catch (TimeoutException e) {
    future.cancel(true);
    throw new RuntimeException("脚本执行超时");
}
```

### 3. 限制内存使用 💾 推荐

```java
// 在Worker线程中执行，限制堆大小
// 启动参数: -Xmx512m
```

### 4. 沙箱隔离 🏝️ 强烈推荐

考虑使用以下方案：

- **GraalVM JavaScript**: 更安全的JavaScript引擎，支持沙箱
- **Docker容器隔离**: 在容器中执行不信任的代码
- **Java SecurityManager**: 配置安全策略文件

### 5. HTTP客户端访问控制 🌐 必须

```java
// 在JsHttpClient中添加URL验证
private boolean isAllowedUrl(String url) {
    // 禁止访问内网地址
    if (url.matches(".*\\b(127\\.0\\.0\\.1|localhost|192\\.168\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.).*")) {
        return false;
    }
    
    // 禁止访问云服务元数据
    if (url.contains("169.254.169.254")) {
        return false;
    }
    
    // 白名单检查
    // return allowedDomains.contains(getDomain(url));
    
    return true;
}
```

### 6. 输入验证 ✅ 必须

```java
// 验证JavaScript代码
private void validateJsCode(String jsCode) {
    // 检查代码长度
    if (jsCode.length() > 100000) {
        throw new IllegalArgumentException("代码过长");
    }
    
    // 检查危险关键词
    List<String> dangerousKeywords = Arrays.asList(
        "Java.type",
        "getClass",
        "getRuntime",
        "exec(",
        "ProcessBuilder",
        "System.exit",
        "Runtime.halt"
    );
    
    for (String keyword : dangerousKeywords) {
        if (jsCode.contains(keyword)) {
            throw new SecurityException("代码包含危险操作: " + keyword);
        }
    }
}
```

### 7. 监控和日志 📊 必须

```java
// 记录所有执行的脚本
log.info("执行脚本 - 用户: {}, IP: {}, 代码哈希: {}", 
    userId, clientIp, DigestUtils.md5Hex(jsCode));

// 监控异常行为
if (executionTime > 10000) {
    log.warn("脚本执行时间过长: {}ms", executionTime);
}
```

### 8. 迁移到GraalVM 🚀 长期建议

Nashorn已在JDK 15中废弃，建议迁移到GraalVM JavaScript：

```xml
<dependency>
    <groupId>org.graalvm.js</groupId>
    <artifactId>js</artifactId>
    <version>23.0.0</version>
</dependency>
```

GraalVM提供更好的安全性和性能：
- 默认沙箱隔离
- 无法访问Java类（除非显式允许）
- 更好的性能
- 活跃维护

## 测试检查清单

执行安全测试时，请确认以下检查项：

- [ ] 测试1: 系统命令执行 - 应该**失败**
- [ ] 测试2: 文件系统访问 - 应该**失败**
- [ ] 测试3: 系统属性访问 - 应该**失败**
- [ ] 测试4: 反射攻击 - 应该**失败**
- [ ] 测试5: 网络Socket - 应该**失败**
- [ ] 测试6: JVM退出 - 应该**失败**
- [ ] 测试7: SSRF攻击 - 应该**部分失败**（禁止内网访问）
- [ ] 测试8: 对象滥用 - 应该**部分失败**（只能访问公开API）
- [ ] 测试9: DOS攻击 - 应该**超时中断**
- [ ] 测试10: 内存溢出 - 应该**抛出OOM或限制**

## 安全评估标准

### 🟢 安全 (A级)
- 所有高危测试都失败
- 有完善的ClassFilter
- 有超时和内存限制
- HTTP客户端有访问控制

### 🟡 基本安全 (B级)
- 大部分高危测试失败
- 无法执行系统命令和文件操作
- 有部分访问控制

### 🟠 存在风险 (C级)
- 某些中危测试通过
- 缺少超时或内存限制
- HTTP客户端无限制

### 🔴 严重不安全 (D级)
- 高危测试通过
- 可以执行系统命令
- 可以读写文件系统
- **不应在生产环境使用**

## 参考资料

- [OWASP - Server Side Request Forgery](https://owasp.org/www-community/attacks/Server_Side_Request_Forgery)
- [Nashorn Security Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/security.html)
- [GraalVM JavaScript Security](https://www.graalvm.org/latest/security-guide/polyglot-sandbox/)
- [Java SecurityManager Documentation](https://docs.oracle.com/javase/tutorial/essential/environment/security.html)

## 联系方式

如果发现新的安全漏洞，请通过安全渠道报告，不要公开披露。

---

**免责声明**: 本文档仅用于安全测试和教育目的。任何人使用这些测试用例造成的损害，作者概不负责。

