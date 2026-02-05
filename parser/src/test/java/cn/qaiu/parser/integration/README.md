# 带认证的网盘解析集成测试

## 📋 概述

这个测试套件用于验证 UC、夸克和小飞机网盘的完整解析流程，包括认证、Cookie 处理和直链获取。

## 🚀 快速开始

### 1. 准备配置文件

```bash
cd parser/src/test/resources
cp auth-test.properties.template auth-test.properties
```

### 2. 填写认证信息

编辑 `auth-test.properties` 文件，填入真实的 Cookie 和分享链接。

**如何获取 Cookie：**

1. 在浏览器中登录对应网盘（夸克/UC）
2. 打开开发者工具（F12）
3. 切换到 Network 标签
4. 刷新页面
5. 找到任意请求，在请求头中复制完整的 Cookie

**夸克网盘 Cookie 示例：**
```
__pus=abc123; __kp=def456; __kps=ghi789; __ktd=jkl012; __uid=mno345; __puus=pqr678
```

**UC 网盘 Cookie 示例：**
```
__pus=xyz123; __kp=uvw456; __kps=rst789; __ktd=opq012; __uid=lmn345; __puus=ijk678
```

### 3. 运行测试

```bash
cd parser
mvn exec:java -Dexec.mainClass="cn.qaiu.parser.integration.AuthParseIntegrationTest" -Dexec.classpathScope=test -q
```

或者使用编译后运行：

```bash
mvn test-compile
java -cp target/test-classes:target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) cn.qaiu.parser.integration.AuthParseIntegrationTest
```

## 📝 配置文件格式

```properties
# 夸克网盘（必须认证）
qk.cookie=__pus=xxx; __kp=xxx; ...
qk.url=https://pan.quark.cn/s/xxxxxxxxxx
qk.pwd=

# UC 网盘（必须认证）
uc.cookie=__pus=xxx; __kp=xxx; ...
uc.url=https://fast.uc.cn/s/xxxxxxxxxx
uc.pwd=

# 小飞机网盘（大文件需认证）
fj.cookie=session_id=xxx
fj.url=https://share.feijipan.com/s/xxxxxxxxxx
fj.pwd=1234
```

## 🧪 测试内容

### 1. 夸克网盘测试
- ✅ Cookie 过滤和应用
- ✅ __puus 自动刷新机制
- ✅ 解析带认证的分享链接
- ✅ 获取直链
- ✅ 验证直链格式

### 2. UC 网盘测试
- ✅ Cookie 过滤和应用
- ✅ __puus 自动刷新机制
- ✅ 解析带认证的分享链接
- ✅ 获取直链
- ✅ 验证直链格式

### 3. 小飞机网盘测试
- ✅ 可选认证配置
- ✅ 解析带密码的分享链接
- ✅ 大文件认证处理
- ✅ 获取直链
- ✅ 验证直链格式

## 📊 测试输出示例

```
========================================
   带认证的解析集成测试
========================================

✓ 配置文件加载成功

=== 测试夸克网盘解析（带认证）===
分享链接: https://pan.quark.cn/s/abc123def
Cookie: __pus=abc1...xyz789

开始解析...

✅ 夸克网盘解析成功!
耗时: 1234ms
直链: https://download.quark.cn/file/xxx
✓ 直链格式正确

=== 测试 UC 网盘解析（带认证）===
分享链接: https://fast.uc.cn/s/def456ghi
Cookie: __pus=def4...uvw012

开始解析...

✅ UC 网盘解析成功!
耗时: 2345ms
直链: https://download.uc.cn/file/xxx
✓ 直链格式正确

========================================
   集成测试完成
========================================
```

## ⚠️ 注意事项

1. **Cookie 安全性**
   - 不要将包含真实 Cookie 的配置文件提交到版本控制
   - `auth-test.properties` 已在 `.gitignore` 中
   - Cookie 包含敏感信息，请妥善保管

2. **Cookie 有效期**
   - Cookie 通常有效期为 1-7 天
   - 过期后需要重新获取
   - 如果解析失败，首先检查 Cookie 是否过期

3. **网盘限制**
   - 夸克和 UC 网盘**必须**提供 Cookie 才能解析
   - 小飞机网盘仅大文件（>100MB）需要 Cookie
   - 部分分享链接可能有下载次数限制

4. **测试环境**
   - 需要网络连接
   - 建议使用真实的大文件分享链接测试
   - 超时时间设置为 30 秒

## 🔍 故障排查

### 解析失败

1. **检查 Cookie 格式**
   - 确保包含所有必需字段：`__pus`, `__kp`, `__kps`, `__ktd`, `__uid`, `__puus`
   - 没有多余的空格或换行符

2. **检查分享链接**
   - 链接格式正确
   - 链接未过期
   - 分享密码正确（如果有）

3. **查看详细日志**
   - 运行时不加 `-q` 参数查看完整日志
   - 检查网络请求和响应

### Cookie 过期

- 重新登录网盘
- 重新获取 Cookie
- 更新配置文件

### 网络超时

- 检查网络连接
- 可能是网盘服务器响应慢
- 可以修改代码中的超时时间（默认30秒）

## 📚 相关文档

- [Cookie 工具类文档](../java/cn/qaiu/util/CookieUtils.java)
- [夸克网盘解析器](../java/cn/qaiu/parser/impl/QkTool.java)
- [UC 网盘解析器](../java/cn/qaiu/parser/impl/UcTool.java)
- [小飞机网盘解析器](../java/cn/qaiu/parser/impl/FjTool.java)
- [认证参数指南](../../doc/auth-param/AUTH_PARAM_GUIDE.md)

## 💡 提示

- 首次运行前确保已执行 `mvn compile` 编译项目
- 如果未配置某个网盘，该网盘的测试会自动跳过
- 测试结果包含解析耗时，可用于性能评估
- Cookie 会自动过滤，只保留必需字段
