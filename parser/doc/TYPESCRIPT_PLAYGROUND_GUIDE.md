# TypeScript 支持文档

## 概述

演练场现在支持 TypeScript！您可以使用现代 TypeScript 语法编写解析器代码，系统会自动将其编译为 ES5 并在后端执行。

## 功能特性

### 🎯 核心功能

- ✅ **TypeScript 编译器集成**：内置 TypeScript 编译器，实时将 TS 代码编译为 ES5
- ✅ **语言选择器**：在演练场工具栏轻松切换 JavaScript 和 TypeScript
- ✅ **编译错误提示**：友好的编译错误提示和建议
- ✅ **双代码存储**：同时保存原始 TypeScript 代码和编译后的 ES5 代码
- ✅ **无缝集成**：与现有演练场功能完全兼容

### 📝 TypeScript 特性支持

支持所有标准 TypeScript 特性，包括但不限于：

- 类型注解（Type Annotations）
- 接口（Interfaces）
- 类型别名（Type Aliases）
- 枚举（Enums）
- 泛型（Generics）
- async/await（编译为 Promise）
- 箭头函数
- 模板字符串
- 解构赋值
- 可选链（Optional Chaining）
- 空值合并（Nullish Coalescing）

## 快速开始

### 1. 选择语言

在演练场工具栏中，点击 **JavaScript** 或 **TypeScript** 按钮选择您要使用的语言。

### 2. 编写代码

选择 TypeScript 后，点击"加载示例"按钮可以加载 TypeScript 示例代码。

#### TypeScript 示例

```typescript
// ==UserScript==
// @name         TypeScript示例解析器
// @type         ts_example_parser
// @displayName  TypeScript示例网盘
// @description  使用TypeScript实现的示例解析器
// @match        https?://example\.com/s/(?<KEY>\w+)
// @author       yourname
// @version      1.0.0
// ==/UserScript==

/**
 * 解析单个文件下载链接
 */
async function parse(
    shareLinkInfo: any,
    http: any,
    logger: any
): Promise<string> {
    const url: string = shareLinkInfo.getShareUrl();
    logger.info(`开始解析: ${url}`);
    
    // 使用fetch API (已在后端实现polyfill)
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`请求失败: ${response.status}`);
        }
        
        const html: string = await response.text();
        
        // 解析逻辑
        const match = html.match(/download-url="([^"]+)"/);
        if (match) {
            return match[1];
        }
        
        return "https://example.com/download/file.zip";
    } catch (error: any) {
        logger.error(`解析失败: ${error.message}`);
        throw error;
    }
}
```

### 3. 运行测试

点击"运行"按钮（或按 Ctrl+Enter）。系统会：

1. 自动检测代码是否为 TypeScript
2. 将 TypeScript 编译为 ES5
3. 显示编译结果（成功/失败）
4. 如果编译成功，使用 ES5 代码执行测试
5. 显示测试结果

### 4. 发布解析器

编译成功后，点击"发布脚本"即可保存解析器。系统会自动：

- 保存原始 TypeScript 代码到 `playground_typescript_code` 表
- 保存编译后的 ES5 代码到 `playground_parser` 表
- 通过 `parserId` 关联两者

## 编译选项

TypeScript 编译器使用以下配置：

```javascript
{
  target: 'ES5',              // 目标版本：ES5
  module: 'None',             // 不使用模块系统
  lib: ['es5', 'dom'],        // 包含ES5和DOM类型定义
  removeComments: false,      // 保留注释
  downlevelIteration: true,   // 支持ES5迭代器降级
  esModuleInterop: true       // 启用ES模块互操作性
}
```

## 类型定义

### 可用的 API 对象

虽然 TypeScript 支持类型注解，但由于后端运行时环境的限制，建议使用 `any` 类型：

```typescript
function parse(
    shareLinkInfo: any,  // 分享链接信息
    http: any,           // HTTP客户端
    logger: any          // 日志对象
): Promise<string> {
    // ...
}
```

### 常用方法

#### shareLinkInfo 对象

```typescript
shareLinkInfo.getShareUrl(): string         // 获取分享URL
shareLinkInfo.getShareKey(): string         // 获取分享Key
shareLinkInfo.getSharePassword(): string    // 获取分享密码
shareLinkInfo.getOtherParam(key: string): any  // 获取其他参数
```

#### logger 对象

```typescript
logger.info(message: string): void    // 记录信息日志
logger.debug(message: string): void   // 记录调试日志
logger.error(message: string): void   // 记录错误日志
logger.warn(message: string): void    // 记录警告日志
```

#### fetch API（后端 Polyfill）

```typescript
async function fetchData(url: string): Promise<any> {
    const response = await fetch(url, {
        method: 'GET',
        headers: {
            'User-Agent': 'Mozilla/5.0...',
            'Content-Type': 'application/json'
        }
    });
    
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    
    const data = await response.json();
    return data;
}
```

## 最佳实践

### 1. 使用类型注解

虽然后端不强制类型检查，但类型注解可以提高代码可读性：

```typescript
function parseFileList(
    shareLinkInfo: any,
    http: any,
    logger: any
): Promise<Array<{
    fileName: string;
    fileId: string;
    size: number;
}>> {
    // 实现...
}
```

### 2. 利用 async/await

TypeScript 的 async/await 会编译为 Promise，后端已实现 Promise polyfill：

```typescript
async function parse(
    shareLinkInfo: any,
    http: any,
    logger: any
): Promise<string> {
    try {
        const response = await fetch(url);
        const data = await response.json();
        return data.downloadUrl;
    } catch (error) {
        logger.error(`错误: ${error.message}`);
        throw error;
    }
}
```

### 3. 使用模板字符串

模板字符串让代码更清晰：

```typescript
logger.info(`开始解析: ${url}, 密码: ${pwd}`);
const apiUrl = `https://api.example.com/file/${fileId}`;
```

### 4. 错误处理

使用类型化的错误处理：

```typescript
try {
    const result = await parseUrl(url);
    return result;
} catch (error: any) {
    logger.error(`解析失败: ${error.message}`);
    throw new Error(`无法解析链接: ${url}`);
}
```

## 编译错误处理

### 常见编译错误

#### 1. 类型不匹配

```typescript
// ❌ 错误
const count: number = "123";

// ✅ 正确
const count: number = 123;
```

#### 2. 缺少返回值

```typescript
// ❌ 错误
function parse(shareLinkInfo: any): string {
    const url = shareLinkInfo.getShareUrl();
    // 缺少 return
}

// ✅ 正确
function parse(shareLinkInfo: any): string {
    const url = shareLinkInfo.getShareUrl();
    return url;
}
```

#### 3. 使用未声明的变量

```typescript
// ❌ 错误
function parse() {
    console.log(unknownVariable);
}

// ✅ 正确
function parse() {
    const knownVariable = "value";
    console.log(knownVariable);
}
```

### 查看编译错误

编译失败时，系统会显示详细的错误信息，包括：

- 错误类型（Error/Warning）
- 错误位置（行号、列号）
- 错误代码（TS错误代码）
- 错误描述

## 数据库结构

### playground_typescript_code 表

存储 TypeScript 源代码的表结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| parser_id | BIGINT | 关联的解析器ID（外键） |
| ts_code | TEXT | TypeScript原始代码 |
| es5_code | TEXT | 编译后的ES5代码 |
| compile_errors | VARCHAR(2000) | 编译错误信息 |
| compiler_version | VARCHAR(32) | 编译器版本 |
| compile_options | VARCHAR(1000) | 编译选项（JSON格式） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| is_valid | BOOLEAN | 编译是否成功 |
| ip | VARCHAR(64) | 创建者IP |

### 与 playground_parser 表的关系

- `playground_typescript_code.parser_id` 外键关联到 `playground_parser.id`
- 一个解析器（parser）可以有一个对应的 TypeScript 代码记录
- 编译后的 ES5 代码存储在 `playground_parser.js_code` 字段中

## API 端点

### 保存 TypeScript 代码

```http
POST /v2/playground/typescript
Content-Type: application/json

{
  "parserId": 1,
  "tsCode": "...",
  "es5Code": "...",
  "compileErrors": null,
  "compilerVersion": "5.x",
  "compileOptions": "{}",
  "isValid": true
}
```

### 获取 TypeScript 代码

```http
GET /v2/playground/typescript/:parserId
```

### 更新 TypeScript 代码

```http
PUT /v2/playground/typescript/:parserId
Content-Type: application/json

{
  "tsCode": "...",
  "es5Code": "...",
  "compileErrors": null,
  "compilerVersion": "5.x",
  "compileOptions": "{}",
  "isValid": true
}
```

## 迁移指南

### 从 JavaScript 迁移到 TypeScript

1. **添加类型注解**：
   ```typescript
   // JavaScript
   function parse(shareLinkInfo, http, logger) {
       var url = shareLinkInfo.getShareUrl();
       return url;
   }
   
   // TypeScript
   function parse(
       shareLinkInfo: any,
       http: any,
       logger: any
   ): string {
       const url: string = shareLinkInfo.getShareUrl();
       return url;
   }
   ```

2. **使用 const/let 替代 var**：
   ```typescript
   // JavaScript
   var url = "https://example.com";
   var count = 0;
   
   // TypeScript
   const url: string = "https://example.com";
   let count: number = 0;
   ```

3. **使用模板字符串**：
   ```typescript
   // JavaScript
   var message = "URL: " + url + ", Count: " + count;
   
   // TypeScript
   const message: string = `URL: ${url}, Count: ${count}`;
   ```

4. **使用 async/await**：
   ```typescript
   // JavaScript
   function parse(shareLinkInfo, http, logger) {
       return new Promise(function(resolve, reject) {
           fetch(url).then(function(response) {
               resolve(response.text());
           }).catch(reject);
       });
   }
   
   // TypeScript
   async function parse(
       shareLinkInfo: any,
       http: any,
       logger: any
   ): Promise<string> {
       const response = await fetch(url);
       return await response.text();
   }
   ```

## 常见问题

### Q: TypeScript 代码会在哪里编译？

A: TypeScript 代码在浏览器前端编译为 ES5，然后发送到后端执行。这确保了后端始终执行标准的 ES5 代码。

### Q: 编译需要多长时间？

A: 通常在几毫秒到几百毫秒之间，取决于代码大小和复杂度。

### Q: 可以使用 npm 包吗？

A: 不可以。目前不支持 import/require 外部模块。所有代码必须自包含。

### Q: 类型检查严格吗？

A: 不严格。编译器配置为允许隐式 any 类型，不进行严格的 null 检查。主要目的是支持现代语法，而非严格的类型安全。

### Q: 编译后的代码可以查看吗？

A: 目前编译后的 ES5 代码存储在数据库中，但 UI 中暂未提供预览功能。这是未来的增强计划。

### Q: 原有的 JavaScript 代码会受影响吗？

A: 不会。JavaScript 和 TypeScript 模式完全独立，互不影响。

## 故障排除

### 编译失败

1. **检查语法**：确保 TypeScript 语法正确
2. **查看错误信息**：仔细阅读编译错误提示
3. **简化代码**：从简单的示例开始，逐步添加功能
4. **使用示例**：点击"加载示例"查看正确的代码结构

### 运行时错误

1. **检查 ES5 兼容性**：某些高级特性可能无法完全转换
2. **验证 API 使用**：确保正确使用 shareLinkInfo、http、logger 等对象
3. **查看日志**：使用 logger 对象输出调试信息

## 未来计划

- [ ] 显示编译后的 ES5 代码预览
- [ ] 添加专用的编译错误面板
- [ ] 支持更多 TypeScript 配置选项
- [ ] 提供完整的类型定义文件（.d.ts）
- [ ] 支持代码自动补全和智能提示
- [ ] 添加 TypeScript 代码片段库

## 反馈与支持

如遇到问题或有建议，请在 GitHub Issues 中提出：
https://github.com/qaiu/netdisk-fast-download/issues
