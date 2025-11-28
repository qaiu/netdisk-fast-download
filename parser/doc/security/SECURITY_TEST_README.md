# JavaScript执行器安全测试

## 📋 概述

本目录提供了完整的JavaScript执行器安全测试工具和文档，用于验证演练场执行器是否存在安全漏洞。

## 🎯 测试目标

验证以下安全风险：

| 测试项目 | 危险级别 | 说明 |
|---------|---------|------|
| 系统命令执行 | 🔴 极高 | 验证是否能执行shell命令 |
| 文件系统访问 | 🔴 极高 | 验证是否能读写本地文件 |
| 系统属性访问 | 🟡 高 | 验证是否能获取系统信息 |
| 反射攻击 | 🔴 极高 | 验证是否能通过反射绕过限制 |
| 网络Socket | 🔴 极高 | 验证是否能创建任意网络连接 |
| JVM退出 | 🔴 极高 | 验证是否能终止应用 |
| SSRF攻击 | 🟡 高 | 验证HTTP客户端访问控制 |

## 📂 测试资源

```
parser/
├── src/test/java/cn/qaiu/parser/
│   └── SecurityTest.java              # JUnit测试用例（7个测试方法）
├── doc/
│   └── SECURITY_TESTING_GUIDE.md      # 详细测试指南和安全建议
├── test-security.sh                   # 快速执行脚本
└── SECURITY_TEST_README.md           # 本文件

web-service/src/test/resources/
└── playground-security-tests.http     # HTTP接口测试用例（10个测试）
```

## 🚀 快速开始

### 方式1: 使用Shell脚本（推荐）

```bash
cd parser
chmod +x test-security.sh
./test-security.sh
```

### 方式2: Maven命令

```bash
cd parser
mvn test -Dtest=SecurityTest
```

### 方式3: HTTP接口测试

1. 启动应用服务器
2. 打开 `web-service/src/test/resources/playground-security-tests.http`
3. 在IDE中逐个执行测试用例

## 📊 预期结果

### ✅ 安全系统（预期）

所有高危测试应该**失败**，日志中应该显示：

```
[INFO] 尝试执行系统命令...
[INFO] Runtime.exec失败: ReferenceError: "Java" is not defined
[INFO] ProcessBuilder失败: ReferenceError: "Java" is not defined
✓ 安全: 无法执行系统命令
```

### ❌ 不安全系统（需要修复）

如果看到以下日志，说明存在严重安全漏洞：

```
[ERROR] 【安全漏洞】成功执行系统命令: root
危险: 系统命令执行成功
```

## ⚠️ 重要警告

1. **仅在测试环境执行** - 这些测试包含危险代码
2. **不要在生产环境运行** - 可能导致系统被攻击
3. **发现漏洞立即修复** - 不要在公开环境部署有漏洞的版本

## 🔧 安全修复建议

如果测试发现安全问题，请参考 `doc/SECURITY_TESTING_GUIDE.md` 中的修复方案：

### 最关键的修复措施

1. **实现ClassFilter** - 禁止JavaScript访问危险Java类
2. **添加超时机制** - 防止DOS攻击
3. **HTTP白名单** - 防止SSRF攻击
4. **迁移到GraalVM** - 使用更安全的JavaScript引擎

### 示例：ClassFilter实现

```java
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

public class SecurityClassFilter implements ClassFilter {
    @Override
    public boolean exposeToScripts(String className) {
        // 禁止所有Java类访问
        return false;
    }
}

// 创建安全的引擎
NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
ScriptEngine engine = factory.getScriptEngine(new SecurityClassFilter());
```

## 📖 详细文档

完整的安全测试指南、修复方案和最佳实践，请查看：

👉 **[doc/SECURITY_TESTING_GUIDE.md](doc/SECURITY_TESTING_GUIDE.md)**

该文档包含：
- 每个测试用例的详细说明
- 潜在风险分析
- 完整的修复方案
- 安全配置最佳实践
- GraalVM迁移指南

## 🔍 测试检查清单

执行测试后，请确认：

- [ ] ✅ 测试1: 系统命令执行 - **失败**（安全）
- [ ] ✅ 测试2: 文件系统访问 - **失败**（安全）
- [ ] ✅ 测试3: 系统属性访问 - **失败**（安全）
- [ ] ✅ 测试4: 反射攻击 - **失败**（安全）
- [ ] ✅ 测试5: 网络Socket - **失败**（安全）
- [ ] ✅ 测试6: JVM退出 - **失败**（安全）
- [ ] ⚠️  测试7: SSRF攻击 - **部分失败**（禁止内网访问）

## 💡 常见问题

### Q: 为什么要进行这些测试？

A: JavaScript执行器允许运行用户提供的代码，如果不加限制，恶意用户可能：
- 执行系统命令窃取数据
- 读取敏感文件
- 攻击内网服务器
- 导致服务器崩溃

### Q: 测试失败是好事还是坏事？

A: **测试失败是好事！** 这意味着危险操作被成功阻止了。如果测试通过（返回"危险"），说明存在安全漏洞。

### Q: 可以跳过这些测试吗？

A: **强烈不建议！** 如果系统对外提供JavaScript执行功能，必须进行安全测试。否则可能导致严重的安全事故。

### Q: Nashorn已经废弃了，应该怎么办？

A: 建议迁移到 **GraalVM JavaScript**，它提供：
- 更好的安全性（默认沙箱）
- 更好的性能
- 活跃的维护和更新

## 🆘 需要帮助？

如果测试发现安全问题或需要修复建议：

1. 查看详细文档：`doc/SECURITY_TESTING_GUIDE.md`
2. 参考HTTP测试用例：`web-service/src/test/resources/playground-security-tests.http`
3. 检查JUnit测试代码：`src/test/java/cn/qaiu/parser/SecurityTest.java`

---

**最后更新**: 2025-11-28  
**作者**: QAIU  
**许可**: MIT License

