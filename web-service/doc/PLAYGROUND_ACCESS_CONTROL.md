# Playground 访问控制配置指南

## 概述

Playground 演练场是一个用于编写、测试和发布 JavaScript 解析脚本的在线开发环境。为了提升安全性，现在支持灵活的访问控制配置。

## 配置说明

在 `web-service/src/main/resources/app-dev.yml` 文件中添加以下配置：

```yaml
# Playground演练场配置
playground:
  # 是否启用Playground，默认关闭
  enabled: false
  # 访问密码，可选。仅在enabled=true时生效
  # 为空时表示公开访问，不需要密码
  password: ""
```

### 配置参数

#### `enabled`
- **类型**: `boolean`
- **默认值**: `false`
- **说明**: 控制 Playground 功能是否启用
  - `false`: Playground 完全关闭，页面和所有相关 API 均不可访问
  - `true`: Playground 启用，可以正常使用

#### `password`
- **类型**: `string`
- **默认值**: `""` (空字符串)
- **说明**: 访问密码（仅在 `enabled = true` 时生效）
  - 空字符串或 `null`: 公开访问模式，无需密码
  - 非空字符串: 需要输入正确密码才能访问

## 访问模式

### 1. 完全禁用模式 (enabled = false)

这是**默认且推荐的生产环境配置**。

```yaml
playground:
  enabled: false
  password: ""
```

**行为**:
- `/playground` 页面显示"Playground未开启"提示
- 所有 Playground 相关 API（`/v2/playground/**`）返回错误提示
- 最安全的模式，适合生产环境

**适用场景**:
- 生产环境部署
- 不需要使用 Playground 功能的情况

---

### 2. 密码保护模式 (enabled = true, password 非空)

这是**公网环境的推荐配置**。

```yaml
playground:
  enabled: true
  password: "your_strong_password_here"
```

**行为**:
- 访问 `/playground` 页面时会显示密码输入框
- 需要输入正确密码才能进入编辑器
- 密码验证通过后，在当前会话中保持已登录状态
- 会话基于客户端 IP 或 Cookie 进行识别

**适用场景**:
- 需要在公网环境使用 Playground
- 多人共享访问，但需要访问控制
- 团队协作开发环境

**示例**:
```yaml
playground:
  enabled: true
  password: "MySecure@Password123"
```

---

### 3. 公开访问模式 (enabled = true, password 为空)

⚠️ **仅建议在本地开发或内网环境使用**。

```yaml
playground:
  enabled: true
  password: ""
```

**行为**:
- Playground 对所有访问者开放
- 无需输入密码即可使用所有功能
- 页面加载后直接显示编辑器

**适用场景**:
- 本地开发环境（`localhost`）
- 完全隔离的内网环境
- 个人使用且不暴露在公网

**⚠️ 安全警告**:
> **强烈不建议在公网环境下使用此配置！**
> 
> 公开访问模式允许任何人：
> - 执行任意 JavaScript 代码（虽然有沙箱限制）
> - 发布解析器脚本到数据库
> - 查看、修改、删除已有的解析器
> - 可能导致服务器资源被滥用
>
> 如果必须在公网环境开启 Playground，请务必：
> 1. 设置一个足够复杂的密码
> 2. 定期更换密码
> 3. 通过防火墙或网关限制访问来源（IP 白名单）
> 4. 启用访问日志监控
> 5. 考虑使用 HTTPS 加密传输

---

## 技术实现

### 后端实现

#### 状态检查 API
```
GET /v2/playground/status
```

返回：
```json
{
  "enabled": true,
  "needPassword": true,
  "authed": false
}
```

#### 登录 API
```
POST /v2/playground/login
Content-Type: application/json

{
  "password": "your_password"
}
```

#### 认证机制
- 使用 Vert.x 的 `SharedData` 存储认证状态
- 基于客户端 IP 或 Cookie 中的 session ID 识别用户
- 密码验证通过后在 `playground_auth` Map 中记录

#### 受保护的端点
所有以下端点都需要通过访问控制检查：
- `POST /v2/playground/test` - 执行测试
- `GET /v2/playground/types.js` - 获取类型定义
- `GET /v2/playground/parsers` - 获取解析器列表
- `POST /v2/playground/parsers` - 保存解析器
- `PUT /v2/playground/parsers/:id` - 更新解析器
- `DELETE /v2/playground/parsers/:id` - 删除解析器
- `GET /v2/playground/parsers/:id` - 获取解析器详情

### 前端实现

#### 状态检查流程
1. 页面加载时调用 `/v2/playground/status`
2. 根据返回的状态显示不同界面：
   - `enabled = false`: 显示"未开启"提示
   - `enabled = true & needPassword = true & !authed`: 显示密码输入框
   - `enabled = true & (!needPassword || authed)`: 加载编辑器

#### 密码输入界面
- 密码输入框（支持显示/隐藏密码）
- 验证按钮
- 错误提示
- 支持回车键提交

---

## 配置示例

### 示例 1: 生产环境（推荐）
```yaml
playground:
  enabled: false
  password: ""
```

### 示例 2: 公网开发环境
```yaml
playground:
  enabled: true
  password: "Str0ng!P@ssw0rd#2024"
```

### 示例 3: 本地开发
```yaml
playground:
  enabled: true
  password: ""
```

---

## 常见问题

### Q1: 忘记密码怎么办？
**A**: 直接修改 `app-dev.yml` 配置文件中的 `password` 值，然后重启服务即可。

### Q2: 可以动态修改密码吗？
**A**: 目前需要修改配置文件并重启服务。密码在服务启动时加载。

### Q3: 多个用户可以同时使用吗？
**A**: 可以。密码验证通过后，每个客户端都会保持独立的认证状态。

### Q4: 公开模式下有什么安全限制吗？
**A**: 
- 代码执行有大小限制（128KB）
- JavaScript 在 Nashorn 沙箱中运行
- 最多创建 100 个解析器
- 但仍然建议在内网或本地使用

### Q5: 密码会明文传输吗？
**A**: 如果使用 HTTP，是的。强烈建议配置 HTTPS 以加密传输。

### Q6: 会话会过期吗？
**A**: 当前实现基于内存存储，服务重启后需要重新登录。单个会话不会主动过期。

---

## 安全建议

### 🔒 强密码要求
如果启用密码保护，请确保密码符合以下要求：
- 长度至少 12 位
- 包含大小写字母、数字和特殊字符
- 避免使用常见单词或生日等个人信息
- 定期更换密码（建议每季度一次）

### 🌐 网络安全措施
- 在生产环境使用 HTTPS
- 配置防火墙或网关限制访问来源
- 使用反向代理（如 Nginx）添加额外的安全层
- 启用访问日志和监控

### 📝 最佳实践
1. **默认禁用**: 除非必要，保持 `enabled: false`
2. **密码保护**: 公网环境务必设置密码
3. **访问控制**: 结合 IP 白名单限制访问
4. **定期审计**: 检查已创建的解析器脚本
5. **监控告警**: 设置异常访问告警机制

---

## 迁移指南

### 从旧版本升级

如果你的系统之前没有 Playground 访问控制，升级后：

1. **默认行为**: Playground 将被禁用（`enabled: false`）
2. **如需启用**: 在配置文件中添加上述配置
3. **兼容性**: 完全向后兼容，不影响其他功能

---

## 更新日志

### v0.1.8
- 添加 Playground 访问控制功能
- 支持三种访问模式：禁用、密码保护、公开访问
- 前端添加密码输入界面
- 所有 Playground API 受保护

---

## 技术支持

如有问题，请访问：
- GitHub Issues: https://github.com/qaiu/netdisk-fast-download/issues
- 项目文档: https://github.com/qaiu/netdisk-fast-download

---

**最后更新**: 2025-12-07
