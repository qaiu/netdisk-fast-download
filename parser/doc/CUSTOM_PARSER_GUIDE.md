# 自定义解析器扩展指南

> 最后更新：2025-10-17

## 概述

本模块支持用户自定义解析器扩展。用户在依赖本项目的 Maven 坐标后，可以实现自己的网盘解析器并注册到系统中使用。

## 核心组件

### 1. CustomParserConfig
自定义解析器配置类，用于描述自定义解析器的元信息。

### 2. CustomParserRegistry
自定义解析器注册中心，用于管理所有已注册的自定义解析器。

### 3. ParserCreate
解析器工厂类，已增强支持自定义解析器的创建。

## 使用步骤

### 步骤1: 添加 Maven 依赖

```xml
<dependency>
    <groupId>cn.qaiu</groupId>
    <artifactId>parser</artifactId>
    <version>10.1.17</version>
</dependency>
```

### 步骤2: 继承 PanBase 抽象类

创建自己的解析工具类，**必须继承 `PanBase` 抽象类**（而不是直接实现 IPanTool 接口）。PanBase 提供了丰富的工具方法和 HTTP 客户端，简化解析器的开发。

```java
package com.example.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

/**
 * 自定义网盘解析器示例
 */
public class MyCustomPanTool extends PanBase {
    
    /**
     * 必须提供 ShareLinkInfo 单参构造器
     */
    public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }
    
    @Override
    public Future<String> parse() {
        // 使用 PanBase 提供的 HTTP 客户端发起请求
        String shareKey = shareLinkInfo.getShareKey();
        String sharePassword = shareLinkInfo.getSharePassword();
        
        // 示例：使用 client 发起 GET 请求
        client.getAbs("https://your-pan-domain.com/api/share/" + shareKey)
            .send()
            .onSuccess(res -> {
                // 使用 asJson 方法将响应转换为 JSON
                var json = asJson(res);
                String downloadUrl = json.getString("download_url");
                
                // 使用 complete 方法完成 Promise
                complete(downloadUrl);
            })
            .onFailure(handleFail("请求下载链接失败"));
        
        // 返回 Future
        return future();
    }
    
    /**
     * 如果需要解析文件列表，可以重写此方法
     */
    @Override
    public Future<List<FileInfo>> parseFileList() {
        // 实现文件列表解析逻辑
        return super.parseFileList();
    }
    
    /**
     * 如果需要根据文件ID获取下载链接，可以重写此方法
     */
    @Override
    public Future<String> parseById() {
        // 实现根据ID解析的逻辑
        return super.parseById();
    }
}
```

### PanBase 提供的核心方法

PanBase 为解析器开发提供了以下工具和方法：

#### HTTP 客户端
- **`client`**: 标准 WebClient 实例，支持自动重定向
- **`clientSession`**: 带会话管理的 WebClient，自动处理 Cookie
- **`clientNoRedirects`**: 不自动重定向的 WebClient，用于需要手动处理重定向的场景

#### 响应处理
- **`asJson(HttpResponse)`**: 将 HTTP 响应转换为 JsonObject，自动处理 gzip 压缩和异常
- **`asText(HttpResponse)`**: 将 HTTP 响应转换为文本，自动处理 gzip 压缩

#### Promise 管理
- **`complete(String)`**: 完成 Promise 并返回结果
- **`future()`**: 获取 Promise 的 Future 对象
- **`fail(String, Object...)`**: Promise 失败时记录错误信息
- **`fail(Throwable, String, Object...)`**: Promise 失败时记录错误信息和异常
- **`handleFail(String)`**: 生成失败处理器，用于请求的 onFailure 回调

#### 其他工具
- **`nextParser()`**: 调用下一个解析器，用于通用域名解析转发
- **`getDomainName()`**: 获取域名名称
- **`shareLinkInfo`**: 分享链接信息对象，包含 shareKey、sharePassword 等
- **`log`**: 日志记录器

### WebClient 请求流程

WebClient 是基于 Vert.x 的异步 HTTP 客户端，其请求流程如下：

1. **初始化 Vert.x 实例**
   ```java
   Vertx vertx = Vertx.vertx();
   WebClientVertxInit.init(vertx);
   ```

2. **创建解析器实例**
   - 继承 PanBase 的解析器会自动获得配置好的 WebClient 实例

3. **发起异步请求**
   ```java
   client.getAbs("https://api.example.com/endpoint")
       .putHeader("User-Agent", "MyParser/1.0")
       .send()
       .onSuccess(res -> {
           // 处理成功响应
           JsonObject json = asJson(res);
           complete(json.getString("url"));
       })
       .onFailure(handleFail("请求失败"));
   ```

4. **请求流程说明**
   - **GET 请求**: 使用 `client.getAbs(url).send()`
   - **POST 请求**: 使用 `client.postAbs(url).sendJson(body)` 或 `.sendForm(form)`
   - **会话请求**: 使用 `clientSession` 自动管理 Cookie
   - **禁用重定向**: 使用 `clientNoRedirects` 手动处理 302/301
   - **代理支持**: PanBase 构造器会自动处理 shareLinkInfo 中的代理配置

5. **响应处理**
   ```java
   .onSuccess(res -> {
       // 检查状态码
       if (res.statusCode() != 200) {
           fail("请求失败，状态码：" + res.statusCode());
           return;
       }
       
       // 解析 JSON 响应
       JsonObject json = asJson(res);
       
       // 或解析文本响应
       String text = asText(res);
       
       // 完成 Promise
       complete(result);
   })
   .onFailure(handleFail("网络请求异常"));
   ```

6. **错误处理**
   - 使用 `fail()` 方法标记解析失败
   - 使用 `handleFail()` 生成统一的失败处理器
   - 所有异常会自动记录到日志

### 步骤3: 注册自定义解析器

在应用启动时注册你的解析器：

```java
import cn.qaiu.parser.CustomParserConfig;
import cn.qaiu.parser.CustomParserRegistry;
import com.example.parser.MyCustomPanTool;

public class Application {
    
    public static void main(String[] args) {
        // 注册自定义解析器
        registerCustomParsers();
        
        // 启动你的应用...
    }
    
    private static void registerCustomParsers() {
        // 创建自定义解析器配置
        CustomParserConfig config = CustomParserConfig.builder()
                .type("mypan")  // 类型标识（必填，唯一，建议小写）
                .displayName("我的网盘")  // 显示名称（必填）
                .toolClass(MyCustomPanTool.class)  // 解析工具类（必填）
                .standardUrlTemplate("https://mypan.com/s/{shareKey}")  // URL模板（可选）
                .panDomain("https://mypan.com")  // 网盘域名（可选）
                .build();
        
        // 注册到系统
        CustomParserRegistry.register(config);
        
        System.out.println("自定义解析器注册成功！");
    }
}
```

### 步骤4: 使用自定义解析器

**重要：自定义解析器只能通过 `fromType` 方法创建，不支持从分享链接自动识别。**

```java
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.IPanTool;

public class Example {
    
    public static void main(String[] args) {
        // 方式1: 使用 fromType 创建（推荐）
        IPanTool tool = ParserCreate.fromType("mypan")  // 使用注册时的type
                .shareKey("abc123")  // 设置分享键
                .setShareLinkInfoPwd("1234")  // 设置密码（可选）
                .createTool();  // 创建工具实例
        
        // 方式1: 使用同步方法解析（推荐）
        String downloadUrl = tool.parseSync();
        System.out.println("下载链接: " + downloadUrl);
        
        // 方式2: 使用同步方法解析文件列表
        List<FileInfo> files = tool.parseFileListSync();
        System.out.println("文件列表: " + files.size() + " 个文件");
        
        // 方式3: 使用同步方法根据文件ID获取下载链接
        if (!files.isEmpty()) {
            String fileId = files.get(0).getFileId();
            String fileDownloadUrl = tool.parseByIdSync();
            System.out.println("文件下载链接: " + fileDownloadUrl);
        }
        
        // 方式4: 异步解析（仍支持）
        tool.parse().onSuccess(url -> {
            System.out.println("异步获取下载链接: " + url);
        }).onFailure(err -> {
            System.err.println("解析失败: " + err.getMessage());
        });
    }
}
```

## 同步方法支持

解析器现在支持三种同步方法，简化了使用方式：

### 1. parseSync()
解析单个文件的下载链接：
```java
String downloadUrl = tool.parseSync();
```

### 2. parseFileListSync()
解析文件列表（目录）：
```java
List<FileInfo> files = tool.parseFileListSync();
for (FileInfo file : files) {
    System.out.println("文件: " + file.getFileName());
}
```

### 3. parseByIdSync()
根据文件ID获取下载链接：
```java
String fileDownloadUrl = tool.parseByIdSync();
```

### 同步方法优势
- **简化使用**: 无需处理 Future 和回调
- **异常处理**: 同步方法会抛出异常，便于错误处理
- **代码简洁**: 减少异步代码的复杂性

### 异步方法仍可用
原有的异步方法仍然支持：
- `parse()`: 返回 `Future<String>`
- `parseFileList()`: 返回 `Future<List<FileInfo>>`
- `parseById()`: 返回 `Future<String>`

## 注意事项

### 1. 类型标识规范
- 类型标识（type）必须唯一
- 建议使用小写英文字母
- 不能与内置解析器类型冲突
- 注册时会自动检查冲突

### 2. 构造器要求
自定义解析器类必须提供 `ShareLinkInfo` 单参构造器，并调用父类构造器：
```java
public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
    super(shareLinkInfo);
}
```

### 3. 创建方式限制
- ✅ **支持：** 通过 `ParserCreate.fromType("type")` 创建
- ❌ **不支持：** 通过 `ParserCreate.fromShareUrl(url)` 自动识别

这是因为自定义解析器没有正则表达式模式来匹配分享链接。

### 4. 线程安全
`CustomParserRegistry` 使用 `ConcurrentHashMap` 实现，支持多线程安全的注册和查询。

## API 参考

### CustomParserConfig.Builder

| 方法 | 说明 | 必填 |
|------|------|------|
| `type(String)` | 设置类型标识，必须唯一 | 是 |
| `displayName(String)` | 设置显示名称 | 是 |
| `toolClass(Class)` | 设置解析工具类 | 是 |
| `standardUrlTemplate(String)` | 设置标准URL模板 | 否 |
| `panDomain(String)` | 设置网盘域名 | 否 |
| `build()` | 构建配置对象 | - |

### CustomParserRegistry

| 方法 | 说明 |
|------|------|
| `register(CustomParserConfig)` | 注册自定义解析器 |
| `unregister(String type)` | 注销指定类型的解析器 |
| `get(String type)` | 获取指定类型的解析器配置 |
| `contains(String type)` | 检查是否已注册 |
| `clear()` | 清空所有自定义解析器 |
| `size()` | 获取已注册数量 |
| `getAll()` | 获取所有已注册配置 |

### ParserCreate 扩展方法

| 方法 | 说明 |
|------|------|
| `isCustomParser()` | 判断是否为自定义解析器 |
| `getCustomParserConfig()` | 获取自定义解析器配置 |
| `getPanDomainTemplate()` | 获取内置解析器模板 |

## 完整示例

```java
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.CustomParserConfig;
import cn.qaiu.parser.CustomParserRegistry;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;

public class CompleteExample {
    
    public static void main(String[] args) {
        // 1. 注册自定义解析器
        registerParser();
        
        // 2. 使用自定义解析器
        useParser();
        
        // 3. 查询注册状态
        checkRegistry();
        
        // 4. 注销解析器（可选）
        // CustomParserRegistry.unregister("mypan");
    }
    
    private static void registerParser() {
        CustomParserConfig config = CustomParserConfig.builder()
                .type("mypan")
                .displayName("我的网盘")
                .toolClass(MyCustomPanTool.class)
                .standardUrlTemplate("https://mypan.com/s/{shareKey}")
                .panDomain("https://mypan.com")
                .build();
        
        try {
            CustomParserRegistry.register(config);
            System.out.println("✓ 解析器注册成功");
        } catch (IllegalArgumentException e) {
            System.err.println("✗ 注册失败: " + e.getMessage());
        }
    }
    
    private static void useParser() {
        try {
            ParserCreate parser = ParserCreate.fromType("mypan")
                    .shareKey("abc123")
                    .setShareLinkInfoPwd("1234");
            
            // 检查是否为自定义解析器
            if (parser.isCustomParser()) {
                System.out.println("✓ 这是一个自定义解析器");
                System.out.println("  配置: " + parser.getCustomParserConfig());
            }
            
            // 创建工具并解析
            IPanTool tool = parser.createTool();
            
            // 使用同步方法解析
            String url = tool.parseSync();
            System.out.println("✓ 下载链接: " + url);
            
            // 解析文件列表
            List<FileInfo> files = tool.parseFileListSync();
            System.out.println("✓ 文件列表: " + files.size() + " 个文件");
            
            // 根据文件ID获取下载链接
            if (!files.isEmpty()) {
                String fileDownloadUrl = tool.parseByIdSync();
                System.out.println("✓ 文件下载链接: " + fileDownloadUrl);
            }
            
        } catch (Exception e) {
            System.err.println("✗ 解析失败: " + e.getMessage());
        }
    }
    
    private static void checkRegistry() {
        System.out.println("\n已注册的自定义解析器:");
        System.out.println("  数量: " + CustomParserRegistry.size());
        
        if (CustomParserRegistry.contains("mypan")) {
            CustomParserConfig config = CustomParserRegistry.get("mypan");
            System.out.println("  - " + config.getType() + ": " + config.getDisplayName());
        }
    }
    
    // 自定义解析器实现（继承 PanBase）
    static class MyCustomPanTool extends PanBase {
        
        public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
            super(shareLinkInfo);
        }
        
        @Override
        public Future<String> parse() {
            // 使用 PanBase 提供的 HTTP 客户端
            String shareKey = shareLinkInfo.getShareKey();
            
            client.getAbs("https://mypan.com/api/share/" + shareKey)
                .send()
                .onSuccess(res -> {
                    // 使用 asJson 解析响应
                    var json = asJson(res);
                    String downloadUrl = json.getString("download_url");
                    
                    // 使用 complete 完成 Promise
                    complete(downloadUrl);
                })
                .onFailure(handleFail("获取下载链接失败"));
            
            return future();
        }
    }
}
```

## 常见问题

### Q1: 如何更新已注册的解析器？
A: 需要先注销再重新注册：
```java
CustomParserRegistry.unregister("mypan");
CustomParserRegistry.register(newConfig);
```

### Q2: 注册时抛出"类型标识已被注册"异常？
A: 该类型已被使用，请更换其他类型标识或先注销已有的。

### Q3: 注册时抛出"与内置解析器冲突"异常？
A: 你使用的类型标识与系统内置的解析器类型冲突，请查看 `PanDomainTemplate` 枚举了解所有内置类型。

### Q4: 可以从分享链接自动识别我的自定义解析器吗？
A: 不可以。自定义解析器只能通过 `fromType` 方法创建。如果需要从链接识别，建议提交 PR 将解析器添加到 `PanDomainTemplate` 枚举中。

### Q5: 解析器需要依赖外部服务怎么办？
A: 可以在解析器类中注入依赖，或使用单例模式管理外部服务连接。

## 贡献

如果你实现了通用的网盘解析器，欢迎提交 PR 将其加入到内置解析器中！

## 许可

本模块遵循项目主LICENSE。

