# ⚠️ Nashorn引擎限制说明

## 问题描述

Nashorn JavaScript引擎（Java 8-14自带）**无法中断正在执行的JavaScript代码**。

这是Nashorn引擎的一个已知限制，无法通过编程方式解决。

## 具体表现

### 症状
当JavaScript代码包含无限循环时：
```javascript
while(true) {
    var x = 1 + 1;
}
```

会出现以下情况：
1. ✅ 30秒后客户端收到超时错误
2. ❌ Worker线程继续执行无限循环
3. ❌ 线程被永久阻塞，无法释放
4. ❌ 日志持续输出线程阻塞警告

### 日志示例
```
WARN -> [-thread-checker] i.vertx.core.impl.BlockedThreadChecker: 
Thread Thread[playground-executor-1,5,main] has been blocked for 60249 ms, time limit is 60000 ms
```

## 为什么无法中断？

### 尝试过的方案
1. ❌ `Thread.interrupt()` - Nashorn不响应中断信号
2. ❌ `Future.cancel(true)` - 无法强制停止Nashorn
3. ❌ `ExecutorService.shutdownNow()` - 只能停止整个线程池
4. ❌ `ScriptContext.setErrorWriter()` - 无法注入中断逻辑
5. ❌ 自定义ClassFilter - 无法过滤语言关键字

### 根本原因
- Nashorn使用JVM字节码执行JavaScript
- 无限循环被编译成JVM字节码级别的跳转
- 没有安全点（Safepoint）可以插入中断检查
- `while(true)` 不会调用任何Java方法，完全在JVM栈内执行

## 现有防护措施

### 1. ✅ 客户端超时（已实现）
```java
executionFuture.toCompletionStage()
    .toCompletableFuture()
    .orTimeout(30, TimeUnit.SECONDS)
```
- 30秒后返回错误给用户
- 用户知道脚本超时
- 但线程仍被阻塞

### 2. ✅ 前端危险代码检测（已实现）
```javascript
// 检测无限循环模式
/while\s*\(\s*true\s*\)/gi
/for\s*\(\s*;\s*;\s*\)/gi
```
- 执行前警告用户
- 需要用户确认
- 依赖用户自觉

### 3. ✅ Worker线程池隔离
- 使用独立的 `playground-executor` 线程池
- 最多16个线程
- 不影响主服务的事件循环

### 4. ✅ 代码长度限制
- 最大128KB代码
- 减少内存消耗
- 但无法防止无限循环

## 影响范围

### 最坏情况
- 16个恶意请求可以耗尽所有Worker线程
- 后续所有Playground请求会等待
- 主服务不受影响（独立线程池）
- 需要重启服务才能恢复

### 实际影响
- 取决于使用场景
- 如果是公开服务，有被滥用风险
- 如果是内部工具，风险较低

## 解决方案

### 短期方案（已实施）
1. ✅ 前端检测和警告
2. ✅ 超时返回错误
3. ✅ 文档说明限制
4. ⚠️ 监控线程阻塞告警
5. ⚠️ 限流（已有RateLimiter）

### 中期方案（建议）
1. 添加IP黑名单机制
2. 添加滥用检测（同一IP多次触发超时）
3. 考虑添加验证码
4. 定期重启被阻塞的线程池

### 长期方案（需大量工作）
1. **迁移到GraalVM JavaScript引擎**
   - 支持CPU时间限制
   - 可以强制中断
   - 更好的性能
   - 但需要额外依赖

2. **使用独立进程执行**
   - 完全隔离
   - 可以强制杀死进程
   - 但复杂度高

3. **代码静态分析**
   - 分析AST检测循环
   - 注入超时检查代码
   - 但可能被绕过

## 运维建议

### 监控指标
```bash
# 监控线程阻塞告警
tail -f logs/*/run.log | grep "Thread blocked"

# 监控超时频率
tail -f logs/*/run.log | grep "JavaScript执行超时"
```

### 告警阈值
- 单个IP 1小时内超时 >3次 → 警告
- Worker线程阻塞 >80% → 严重
- 持续阻塞 >5分钟 → 考虑重启

### 应急方案
```bash
# 重启服务释放被阻塞的线程
./bin/stop.sh
./bin/run.sh
```

## 用户建议

### ✅ 建议的代码模式
```javascript
// 使用有限循环
for(var i = 0; i < 1000; i++) {
    // 处理逻辑
}

// 使用超时保护
var maxIterations = 10000;
var count = 0;
while(condition && count++ < maxIterations) {
    // 处理逻辑
}
```

### ❌ 禁止的代码模式
```javascript
// 无限循环
while(true) { }
for(;;) { }

// 无退出条件的循环
while(someCondition) {
    // someCondition永远为true
}

// 递归炸弹
function boom() { return boom(); }
```

## 相关链接

- [Nashorn Engine Issues](https://github.com/openjdk/nashorn/issues)
- [GraalVM JavaScript](https://www.graalvm.org/javascript/)
- [Java Script Engine Comparison](https://benchmarksgame-team.pages.debian.net/benchmarksgame/)

---

**最后更新**: 2025-11-29  
**状态**: ⚠️ 已知限制，已采取缓解措施  
**建议**: 如需更严格的控制，考虑迁移到GraalVM JavaScript引擎

