# 自定义解析器扩展指南

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

### 步骤2: 实现 IPanTool 接口

创建自己的解析工具类，必须实现 `IPanTool` 接口：

```java
package com.example.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * 自定义网盘解析器示例
 */
public class MyCustomPanTool implements IPanTool {
    
    private final ShareLinkInfo shareLinkInfo;
    
    /**
     * 必须提供 ShareLinkInfo 单参构造器
     */
    public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }
    
    @Override
    public Future<String> parse() {
        Promise<String> promise = Promise.promise();
        
        // 实现你的解析逻辑
        String shareKey = shareLinkInfo.getShareKey();
        String sharePassword = shareLinkInfo.getSharePassword();
        
        try {
            // 调用你的网盘API，获取下载链接
            String downloadUrl = callYourPanApi(shareKey, sharePassword);
            promise.complete(downloadUrl);
        } catch (Exception e) {
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * 如果需要解析文件列表，可以重写此方法
     */
    @Override
    public Future<List<FileInfo>> parseFileList() {
        // 实现文件列表解析逻辑
        return IPanTool.super.parseFileList();
    }
    
    /**
     * 如果需要根据文件ID获取下载链接，可以重写此方法
     */
    @Override
    public Future<String> parseById() {
        // 实现根据ID解析的逻辑
        return IPanTool.super.parseById();
    }
    
    private String callYourPanApi(String shareKey, String password) {
        // 实现你的网盘API调用逻辑
        return "https://your-pan-domain.com/download/" + shareKey;
    }
}
```

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
        
        // 解析获取下载链接
        String downloadUrl = tool.parseSync();
        System.out.println("下载链接: " + downloadUrl);
        
        // 方式2: 异步解析
        tool.parse().onSuccess(url -> {
            System.out.println("异步获取下载链接: " + url);
        }).onFailure(err -> {
            System.err.println("解析失败: " + err.getMessage());
        });
    }
}
```

## 注意事项

### 1. 类型标识规范
- 类型标识（type）必须唯一
- 建议使用小写英文字母
- 不能与内置解析器类型冲突
- 注册时会自动检查冲突

### 2. 构造器要求
自定义解析器类必须提供 `ShareLinkInfo` 单参构造器：
```java
public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
    this.shareLinkInfo = shareLinkInfo;
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
import io.vertx.core.Future;
import io.vertx.core.Promise;

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
            String url = tool.parseSync();
            System.out.println("✓ 下载链接: " + url);
            
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
    
    // 自定义解析器实现
    static class MyCustomPanTool implements IPanTool {
        private final ShareLinkInfo shareLinkInfo;
        
        public MyCustomPanTool(ShareLinkInfo shareLinkInfo) {
            this.shareLinkInfo = shareLinkInfo;
        }
        
        @Override
        public Future<String> parse() {
            Promise<String> promise = Promise.promise();
            
            // 模拟解析逻辑
            String shareKey = shareLinkInfo.getShareKey();
            String downloadUrl = "https://mypan.com/download/" + shareKey;
            
            promise.complete(downloadUrl);
            return promise.future();
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

