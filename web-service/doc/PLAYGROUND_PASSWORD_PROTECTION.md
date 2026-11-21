# Playground 密码保护功能

## 概述

JS解析器演练场现在支持密码保护功能，可以通过配置文件控制是否需要密码才能访问。

## 配置说明

编辑当前启用的配置文件。发行包默认使用 `resources/app-dev.yml`。

```yaml
# JS演练场配置
playground:
  enabled: false
  # 公开模式，默认false需要密码访问，设为true则无需密码
  public: false
  # 私有模式启用前须设置实例专属密码
  password: ''
```

### 配置项说明

- `public`: 布尔值，默认为 `false`
  - `false`: 需要输入密码才能访问演练场（推荐）
  - `true`: 公开访问，无需密码

- `password`: 字符串，访问密码
  - 无默认值。私有模式缺少密码、密码为空白或沿用旧版公开密码时，演练场保持关闭。

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

1. **设置专属密码**：启用私有模式前，请设置 `playground.password` 为自定义的强密码
2. **使用密码保护**：建议保持 `public: false`，避免未授权访问
3. **定期更换密码**：定期更换访问密码以提高安全性
4. **配置文件保护**：确保配置文件的访问权限受到保护

## 系统启动提示

配置文件及启用开关 `NFD_PLAYGROUND_ENABLED` 生效后，服务会检查私有模式密码。若检查失败，日志会提示配置 `playground.password` 并重启服务，其他功能继续启动。显式选择 `public: true` 会允许免密码访问管理接口。

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

### 登录令牌有效期多久？

私有模式的令牌自签发起最多有效 24 小时。服务重启或重新加载演练场配置会使已签发的令牌失效。

## 后续版本计划

未来版本可能会添加以下功能：

- [ ] 支持环境变量配置密码
- [ ] 支持加密存储密码
- [ ] 支持多用户账户系统
- [ ] 支持 Session 超时配置

## 相关文档

- [Playground 使用指南](PLAYGROUND_GUIDE.md)
- [JavaScript 解析器开发指南](parser/doc/JAVASCRIPT_PARSER_GUIDE.md)
- [TypeScript 实现总结](TYPESCRIPT_IMPLEMENTATION_SUMMARY_CN.md)
