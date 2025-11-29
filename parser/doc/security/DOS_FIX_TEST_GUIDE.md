# 🧪 DoS漏洞修复测试指南

## 快速测试

### 启动服务
```bash
cd /Users/q/IdeaProjects/mycode/netdisk-fast-download
./bin/run.sh
```

### 使用测试文件
```
web-service/src/test/resources/playground-dos-tests.http
```

---

## 测试场景

### ✅ 测试1: 正常执行
**预期**：成功返回结果

### ⚠️ 测试2: 代码长度超限  
**预期**：立即返回错误 "代码长度超过限制"

### 🔥 测试3: 无限循环（重点）
**代码**：
```javascript
while(true) {
    var x = 1 + 1;
}
```

**v2优化后的预期行为**：
1. ✅ 前端检测到 `while(true)` 弹出警告对话框
2. ✅ 用户确认后开始执行
3. ✅ 30秒后返回超时错误
4. ✅ 日志只输出一次超时错误
5. ✅ **不再持续输出BlockedThreadChecker警告**
6. ✅ 可以立即执行下一个测试

**v1的问题行为（已修复）**：
- ❌ 日志每秒输出BlockedThreadChecker警告
- ❌ 日志持续滚动，难以追踪其他问题
- ❌ Worker线程被永久占用

### 🔥 测试4: 内存炸弹
**预期**：30秒超时或OutOfMemoryError

### 🔥 测试5: 递归炸弹
**预期**：捕获StackOverflowError

---

## 日志对比

### v1（问题版本）
```
2025-11-29 16:30:41.607 WARN  -> Thread blocked for 60249 ms
2025-11-29 16:30:42.588 WARN  -> Thread blocked for 61250 ms
2025-11-29 16:30:43.593 WARN  -> Thread blocked for 62251 ms
2025-11-29 16:30:44.599 WARN  -> Thread blocked for 63252 ms
... (持续输出)
```

### v2（优化版本）
```
2025-11-29 16:45:00.000 INFO  -> 开始执行parse方法
2025-11-29 16:45:30.000 ERROR -> JavaScript执行超时（超过30秒），可能存在无限循环
2025-11-29 16:45:30.010 DEBUG -> 临时WorkerExecutor已关闭
... (不再输出BlockedThreadChecker警告)
```

---

## 前端体验

### 危险代码警告

当代码包含以下模式时：
- `while(true)`
- `for(;;)`
- `for(var i=0; true;...)`

会弹出对话框：
```
⚠️ 检测到 while(true) 无限循环

这可能导致脚本无法停止并占用服务器资源。

建议修改代码，添加合理的循环退出条件。

确定要继续执行吗？

[取消] [我知道风险，继续执行]
```

---

## 验证清单

### 功能验证
- [ ] 正常代码可以执行
- [ ] 超过128KB的代码被拒绝
- [ ] 无限循环30秒后超时
- [ ] 前端弹出危险代码警告
- [ ] 超时后可以立即执行新测试

### 日志验证
- [ ] 超时只输出一次错误
- [ ] 不再持续输出BlockedThreadChecker警告
- [ ] 临时WorkerExecutor成功关闭

### 性能验证
- [ ] 正常请求响应时间正常
- [ ] 多次无限循环攻击不影响新请求
- [ ] 内存使用稳定

---

## 故障排查

### 问题：日志仍在滚动
**可能原因**：使用的是旧版本代码  
**解决方案**：
```bash
mvn clean install -DskipTests
./bin/stop.sh
./bin/run.sh
```

### 问题：超时时间太短/太长
**调整方法**：修改 `JsPlaygroundExecutor.java`
```java
private static final long EXECUTION_TIMEOUT_SECONDS = 30; // 改为需要的秒数
```

### 问题：前端检测太敏感
**调整方法**：修改 `Playground.vue` 中的 `dangerousPatterns` 数组

---

## 监控命令

### 监控超时事件
```bash
tail -f logs/*/run.log | grep "JavaScript执行超时"
```

### 监控临时Executor创建
```bash
tail -f logs/*/run.log | grep "playground-temp-"
```

### 监控是否还有BlockedThreadChecker警告
```bash
tail -f logs/*/run.log | grep "Thread blocked"
# v2版本：执行超时测试时，应该不再持续输出
```

---

## 成功标志

### ✅ 修复成功的表现
1. 超时错误立即返回给用户（30秒）
2. 日志只输出一次错误
3. BlockedThreadChecker警告不再持续输出
4. 可以立即执行下一个测试
5. 服务保持稳定

### ❌ 修复失败的表现
1. 日志持续每秒输出警告
2. 无法执行新测试
3. 服务响应缓慢

---

**测试文件**: `web-service/src/test/resources/playground-dos-tests.http`  
**重点测试**: 测试3 - 无限循环  
**成功标志**: 日志不再持续滚动 ✅

