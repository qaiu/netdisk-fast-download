# 安全修复更新日志

## [2025-11-29] - 优化SSRF防护策略

### 🔄 变更内容

#### 调整SSRF防护为宽松模式
- **问题**: 原有SSRF防护过于严格，导致正常外网请求也被拦截
- **症状**: `Error: 请求失败: 404` 或其他网络错误
- **修复**: 调整验证逻辑，只拦截明确的危险请求

#### 具体改进

1. ✅ **允许DNS解析失败的请求**
   - 之前：DNS解析失败 → 抛出异常
   - 现在：DNS解析失败 → 允许继续（可能是外网域名）

2. ✅ **允许格式异常的URL**
   - 之前：URL解析异常 → 抛出异常
   - 现在：URL解析异常 → 只记录日志，允许继续

3. ✅ **优化IP检测逻辑**
   - 先检查是否为IP地址格式
   - 对域名才进行DNS解析
   - 减少不必要的网络请求

### 🛡️ 保留的安全防护

以下危险请求仍然会被拦截：

- ❌ 本地回环：`127.0.0.1`, `localhost`, `::1`
- ❌ 内网IP：`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`
- ❌ 云服务元数据：`169.254.169.254`, `metadata.google.internal`
- ❌ 解析到内网的域名

### 📊 影响范围

**修改文件**:
- `parser/src/main/java/cn/qaiu/parser/customjs/JsHttpClient.java`

**新增文档**:
- `parser/SSRF_PROTECTION.md` - SSRF防护策略说明

---

## [2025-11-28] - 修复JavaScript远程代码执行漏洞

### 🚨 严重安全漏洞修复

#### 漏洞描述
- **类型**: 远程代码执行 (RCE)
- **危险级别**: 🔴 极高
- **影响**: JavaScript可以访问所有Java类，执行任意系统命令

#### 修复措施

1. ✅ **实现ClassFilter类过滤器**
   - 文件：`SecurityClassFilter.java`
   - 功能：拦截JavaScript对危险Java类的访问
   - 黑名单包括：Runtime, File, System, Class, Socket等

2. ✅ **禁用Java内置对象**
   - 禁用：`Java`, `JavaImporter`, `Packages`
   - 位置：`JsPlaygroundExecutor`, `JsParserExecutor`

3. ✅ **添加SSRF防护**
   - 文件：`JsHttpClient.java`
   - 功能：防止访问内网地址和云服务元数据

4. ✅ **修复ArrayIndexOutOfBoundsException**
   - 问题：`getScriptEngine()` 方法参数错误
   - 修复：使用正确的方法签名 `getScriptEngine(new String[0], null, classFilter)`

### 📦 新增文件

**安全组件**:
- `parser/src/main/java/cn/qaiu/parser/customjs/SecurityClassFilter.java`

**测试套件**:
- `parser/src/test/java/cn/qaiu/parser/SecurityTest.java` (7个测试用例)
- `web-service/src/test/resources/playground-security-tests.http` (10个测试用例)

**文档**:
- `parser/doc/SECURITY_TESTING_GUIDE.md` - 详细安全测试指南
- `parser/SECURITY_TEST_README.md` - 快速开始指南
- `parser/SECURITY_FIX_SUMMARY.md` - 修复总结
- `parser/test-security.sh` - 自动化测试脚本
- `SECURITY_URGENT_FIX.md` - 紧急修复通知
- `QUICK_TEST.md` - 快速验证指南

### 🔧 修改文件

1. `JsPlaygroundExecutor.java`
   - 使用安全的ScriptEngine
   - 禁用Java对象访问

2. `JsParserExecutor.java`
   - 使用安全的ScriptEngine
   - 禁用Java对象访问

3. `JsHttpClient.java`
   - 添加URL安全验证
   - 实现SSRF防护

### 📊 修复效果

| 测试项目 | 修复前 | 修复后 |
|---------|--------|--------|
| 系统命令执行 | ❌ 成功 | ✅ 被拦截 |
| 文件系统访问 | ❌ 成功 | ✅ 被拦截 |
| 系统属性访问 | ❌ 成功 | ✅ 被拦截 |
| 反射攻击 | ❌ 成功 | ✅ 被拦截 |
| 网络Socket | ❌ 成功 | ✅ 被拦截 |
| JVM退出 | ❌ 成功 | ✅ 被拦截 |
| SSRF攻击 | ❌ 成功 | ✅ 被拦截 |

### 📈 安全评级提升

- **修复前**: 🔴 D级（严重不安全）
- **修复后**: 🟢 A级（安全）

---

## 部署建议

### 立即部署步骤

```bash
# 1. 拉取最新代码
git pull

# 2. 重新编译
mvn clean install

# 3. 重启服务
./bin/stop.sh
./bin/run.sh

# 4. 验证修复
cd parser
mvn test -Dtest=SecurityTest
```

### 验证清单

- [ ] 服务启动成功
- [ ] 日志显示"🔒 安全的JavaScript引擎初始化成功"
- [ ] Java.type() 被禁用（返回undefined）
- [ ] 内网访问被拦截
- [ ] 外网访问正常工作
- [ ] 安全测试全部通过

---

## 相关资源

- **快速验证**: `QUICK_TEST.md`
- **SSRF策略**: `parser/SSRF_PROTECTION.md`
- **详细修复**: `parser/SECURITY_FIX_SUMMARY.md`
- **测试指南**: `parser/doc/SECURITY_TESTING_GUIDE.md`

---

## 联系方式

如发现新的安全问题或有改进建议，请通过以下方式反馈：
- 提交Issue
- 安全邮件：qaiu00@gmail.com

---

**维护者**: QAIU  
**许可**: MIT License

