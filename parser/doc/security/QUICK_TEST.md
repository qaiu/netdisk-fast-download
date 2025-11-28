# 🧪 安全修复快速验证指南

## 修复内容
✅ JavaScript远程代码执行漏洞已修复  
✅ SSRF攻击防护已添加  
✅ 方法调用错误已修复（`ArrayIndexOutOfBoundsException`）

---

## 快速测试步骤

### 1. 重新编译（必须）

```bash
cd /Users/q/IdeaProjects/mycode/netdisk-fast-download
mvn clean install -DskipTests
```

### 2. 重启服务

```bash
# 停止旧服务
./bin/stop.sh

# 启动新服务
./bin/run.sh
```

### 3. 执行安全测试

#### 方式A: 使用HTTP测试文件（推荐）

1. 确保服务已启动（默认端口 6400）
2. 使用IDE打开: `web-service/src/test/resources/playground-security-tests.http`
3. 执行"测试3: 系统属性和环境变量访问"

**期望结果**:
```json
{
  "success": true,
  "result": "✓ 安全: 无法访问系统属性",
  "logs": [
    {
      "level": "INFO",
      "message": "尝试访问系统属性..."
    },
    {
      "level": "INFO", 
      "message": "系统属性访问失败: ReferenceError: \"Java\" is not defined"
    }
  ]
}
```

#### 方式B: 使用JUnit测试

```bash
cd parser
mvn test -Dtest=SecurityTest#testSystemPropertiesAccess
```

**期望输出**:
```
[INFO] 尝试访问系统属性...
[INFO] 方法1失败: ReferenceError: "Java" is not defined
✓ 安全: 无法访问系统属性
测试完成: 系统属性访问测试
```

---

## 验证清单

运行测试后，确认以下几点：

### ✅ 必须通过的检查

- [ ] 服务启动成功，没有 `ArrayIndexOutOfBoundsException`
- [ ] 日志中出现：`🔒 安全的JavaScript引擎初始化成功`
- [ ] JavaScript代码执行正常（parse函数可以调用）
- [ ] 尝试访问 `Java.type()` 时返回错误：`ReferenceError: "Java" is not defined`
- [ ] 尝试访问 `System.getProperty()` 时失败
- [ ] HTTP请求内网地址（如 127.0.0.1）时被拦截

### ⚠️ 如果出现以下情况说明修复失败

- [ ] 服务启动时抛出异常
- [ ] JavaScript可以成功调用 `Java.type()`
- [ ] 可以获取到系统属性（如用户名、HOME目录）
- [ ] 可以访问内网地址（127.0.0.1, 192.168.x.x）

---

## 快速测试用例

### 测试1: 验证Java访问被禁用 ✅

在演练场输入以下代码：

```javascript
// ==UserScript==
// @name         快速安全测试
// @type         test
// @match        https://test.com/*
// ==/UserScript==

function parse(shareLinkInfo, http, logger) {
    logger.info('开始安全测试...');
    
    // 测试1: Java对象
    try {
        if (typeof Java !== 'undefined') {
            logger.error('❌ 失败: Java对象仍然可用');
            return 'FAILED: Java可用';
        }
    } catch (e) {
        logger.info('✅ 通过: Java对象未定义');
    }
    
    // 测试2: JavaImporter
    try {
        if (typeof JavaImporter !== 'undefined') {
            logger.error('❌ 失败: JavaImporter仍然可用');
            return 'FAILED: JavaImporter可用';
        }
    } catch (e) {
        logger.info('✅ 通过: JavaImporter未定义');
    }
    
    // 测试3: Packages
    try {
        if (typeof Packages !== 'undefined') {
            logger.error('❌ 失败: Packages仍然可用');
            return 'FAILED: Packages可用';
        }
    } catch (e) {
        logger.info('✅ 通过: Packages未定义');
    }
    
    logger.info('✅ 所有测试通过！系统安全！');
    return 'SUCCESS: 安全修复生效';
}
```

**期望输出**:
```
[INFO] 开始安全测试...
[INFO] ✅ 通过: Java对象未定义
[INFO] ✅ 通过: JavaImporter未定义
[INFO] ✅ 通过: Packages未定义
[INFO] ✅ 所有测试通过！系统安全！
SUCCESS: 安全修复生效
```

### 测试2: 验证SSRF防护 ✅

```javascript
function parse(shareLinkInfo, http, logger) {
    logger.info('测试SSRF防护...');
    
    // 测试访问内网
    try {
        http.get('http://127.0.0.1:6400/');
        logger.error('❌ 失败: 可以访问内网');
        return 'FAILED: SSRF防护无效';
    } catch (e) {
        if (e.message && e.message.includes('安全拦截')) {
            logger.info('✅ 通过: 内网访问被阻止 - ' + e.message);
            return 'SUCCESS: SSRF防护有效';
        } else {
            logger.warn('⚠️ 警告: 错误但非安全拦截 - ' + e.message);
            return 'WARNING: 未知错误';
        }
    }
}
```

**期望输出**:
```
[INFO] 测试SSRF防护...
[INFO] ✅ 通过: 内网访问被阻止 - SecurityException: 🔒 安全拦截: 禁止访问内网地址
SUCCESS: SSRF防护有效
```

---

## 故障排查

### 问题1: 服务启动失败

```bash
# 检查编译是否成功
ls -la parser/target/parser-*.jar
ls -la web-service/target/*.jar

# 如果没有jar文件，重新编译
mvn clean install
```

### 问题2: ArrayIndexOutOfBoundsException 仍然出现

```bash
# 确认代码已更新
grep -n "new String\[0\]" parser/src/main/java/cn/qaiu/parser/customjs/JsPlaygroundExecutor.java

# 应该看到类似：
# 68:            ScriptEngine engine = factory.getScriptEngine(new String[0], null, new SecurityClassFilter());

# 如果没有，说明代码未更新，重新拉取
```

### 问题3: 测试显示"Java仍然可用"

这是**严重问题**，说明修复未生效：

1. 确认代码已更新
2. 确认重新编译
3. 确认重启服务
4. 检查日志是否有"安全的JavaScript引擎初始化成功"

```bash
# 检查日志
tail -f logs/*/run.log | grep "JavaScript引擎"

# 应该看到：
# 🔒 安全的JavaScript引擎初始化成功（演练场）
```

---

## 一键测试脚本

创建并运行快速测试：

```bash
cd /Users/q/IdeaProjects/mycode/netdisk-fast-download

# 重新编译
echo "📦 重新编译..."
mvn clean install -DskipTests

# 重启服务
echo "🔄 重启服务..."
./bin/stop.sh
sleep 2
./bin/run.sh

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 5

# 运行安全测试
echo "🧪 运行安全测试..."
cd parser
mvn test -Dtest=SecurityTest#testSystemPropertiesAccess

echo ""
echo "✅ 测试完成！请检查上方输出确认安全修复是否生效。"
```

---

## 成功标志

如果看到以下输出，说明修复成功：

```
✅ 服务启动成功
✅ 日志: 🔒 安全的JavaScript引擎初始化成功
✅ 测试: ReferenceError: "Java" is not defined
✅ 测试: ✓ 安全: 无法访问系统属性
✅ 测试: 🔒 安全拦截: 禁止访问内网地址
```

---

## 下一步

测试通过后：
1. ✅ 标记漏洞为"已修复"
2. ✅ 部署到生产环境（如果适用）
3. ✅ 更新安全文档
4. ✅ 通知团队成员

---

**文档**: 
- 详细修复说明: `parser/SECURITY_FIX_SUMMARY.md`
- 紧急修复指南: `SECURITY_URGENT_FIX.md`
- 完整测试指南: `parser/doc/SECURITY_TESTING_GUIDE.md`

**最后更新**: 2025-11-29

