# Playground 密码保护功能

## 概述

JS解析器演练场现在支持密码保护功能，可以通过配置文件控制是否需要密码才能访问。

## 配置说明

在 `web-service/src/main/resources/app-dev.yml` 文件中添加以下配置：

```yaml
# JS演练场配置
playground:
  # 公开模式，默认false需要密码访问，设为true则无需密码
  public: false
  # 访问密码，建议修改默认密码！
  password: 'nfd_playground_2024'
```

### 配置项说明

- `public`: 布尔值，默认为 `false`
  - `false`: 需要输入密码才能访问演练场（推荐）
  - `true`: 公开访问，无需密码

- `password`: 字符串，访问密码
  - 默认密码：`nfd_playground_2024`
  - **强烈建议在生产环境中修改为自定义密码！**

## 功能特点

### 1. 密码保护模式 (public: false)

当 `public` 设置为 `false` 时：

- 访问 `/playground` 页面时会显示密码输入界面
- 必须输入正确的密码才能使用演练场功能
- 密码验证通过后，会话保持登录状态
- 所有演练场相关的 API 接口都受到保护

### 2. 公开模式 (public: true)

当 `public` 设置为 `true` 时：

- 无需输入密码即可访问演练场
- 适用于内网环境或开发测试环境

### 3. 加载动画与进度条

页面加载过程会显示进度条，包括以下阶段：

1. 初始化Vue组件 (0-20%)
2. 加载配置和本地数据 (20-40%)
3. 准备TypeScript编译器 (40-50%)
4. 初始化Monaco Editor (50-80%)
5. 加载完成 (80-100%)

### 4. 移动端适配

- 桌面端：左右分栏布局，可拖拽调整宽度
- 移动端（屏幕宽度 ≤ 768px）：自动切换为上下分栏布局，可拖拽调整高度

## 安全建议

⚠️ **重要安全提示：**

1. **修改默认密码**：在生产环境中，务必修改 `playground.password` 为自定义的强密码
2. **使用密码保护**：建议保持 `public: false`，避免未授权访问
3. **定期更换密码**：定期更换访问密码以提高安全性
4. **配置文件保护**：确保配置文件的访问权限受到保护

## 系统启动提示

当系统启动时，会在日志中显示当前配置：

```
INFO  - Playground配置已加载: public=false, password=已设置
```

如果使用默认密码，会显示警告：

```
WARN  - ⚠️ 警告：您正在使用默认密码，建议修改配置文件中的 playground.password 以确保安全！
```

## API 端点

### 1. 获取状态

```
GET /v2/playground/status
```

返回：
```json
{
  "code": 200,
  "data": {
    "public": false,
    "authed": false
  }
}
```

### 2. 登录

```
POST /v2/playground/login
Content-Type: application/json

{
  "password": "your_password"
}
```

成功响应：
```json
{
  "code": 200,
  "msg": "登录成功",
  "success": true
}
```

失败响应：
```json
{
  "code": 500,
  "msg": "密码错误",
  "success": false
}
```

## 常见问题

### Q: 如何禁用密码保护？

A: 在配置文件中设置 `playground.public: true`

### Q: 忘记密码怎么办？

A: 修改配置文件中的 `playground.password` 为新密码，然后重启服务

### Q: 密码是否加密存储？

A: 当前版本密码以明文形式存储在配置文件中，请确保配置文件的访问权限受到保护

### Q: Session 有效期多久？

A: Session 由 Vert.x 管理，默认在浏览器会话期间有效，关闭浏览器后失效

## 后续版本计划

未来版本可能会添加以下功能：

- [ ] 支持环境变量配置密码
- [ ] 支持加密存储密码
- [ ] 支持多用户账户系统
- [ ] 支持 Token 认证方式
- [ ] 支持 Session 超时配置

## 相关文档

- [Playground 使用指南](PLAYGROUND_GUIDE.md)
- [JavaScript 解析器开发指南](parser/doc/JAVASCRIPT_PARSER_GUIDE.md)
- [TypeScript 实现总结](TYPESCRIPT_IMPLEMENTATION_SUMMARY_CN.md)
