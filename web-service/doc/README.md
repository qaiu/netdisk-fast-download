# API 文档说明

本目录包含网盘快速下载服务的完整 API 文档。

## 文件说明

### 1. API_DOCUMENTATION.md
详细的接口说明文档，包含：
- 所有接口的详细说明
- 请求参数和响应格式
- 使用示例
- 错误处理说明
- 注意事项

### 2. openapi.json
OpenAPI 3.0 规范的 JSON 文件，可用于：
- 导入到 API 测试工具（如 Apifox、Postman、Swagger UI 等）
- 生成客户端 SDK
- API 文档自动生成

### 3. CLIENT_LINKS_API.md
客户端下载链接 API 的详细说明文档（已存在）

## 如何使用

### 导入到 Apifox

1. 打开 Apifox
2. 选择项目 → 导入
3. 选择 "OpenAPI" 格式
4. 选择 `openapi.json` 文件
5. 点击导入

### 导入到 Postman

1. 打开 Postman
2. 点击 Import
3. 选择 "File" 标签
4. 选择 `openapi.json` 文件
5. 点击 Import

### 使用 Swagger UI 查看

1. 访问 [Swagger Editor](https://editor.swagger.io/)
2. 将 `openapi.json` 的内容复制粘贴到编辑器中
3. 即可查看和测试所有接口

## 接口分类

### 解析相关接口
- `/parser` - 解析分享链接（重定向）
- `/json/parser` - 解析分享链接（JSON）
- `/:type/:key` - 根据类型和Key解析（重定向）
- `/json/:type/:key` - 根据类型和Key解析（JSON）
- `/v2/linkInfo` - 获取链接信息

### 文件列表接口
- `/v2/getFileList` - 获取文件列表

### 预览接口
- `/v2/view/:type/:key` - 预览媒体文件（按类型和Key）
- `/v2/preview` - 预览媒体文件（按URL）
- `/v2/viewUrl/:type/:param` - 预览URL（目录预览）

### 客户端下载链接接口
- `/v2/clientLinks` - 获取所有客户端下载链接
- `/v2/clientLink` - 获取指定类型的客户端下载链接

### 统计信息接口
- `/v2/statisticsInfo` - 获取统计信息

### 网盘列表接口
- `/v2/getPanList` - 获取支持的网盘列表

### 版本信息接口
- `/v2/build-version` - 获取版本号

### 隔空喊话接口
- `/v2/shout/submit` - 提交消息
- `/v2/shout/retrieve` - 检索消息

### 快捷下载接口
- `/d/:type/:key` - 下载重定向（短链）
- `/v2/redirectUrl/:type/:param` - 重定向下载URL（目录文件）

## 更新日志

- 2025-01-21: 初始版本，包含所有接口的完整文档


