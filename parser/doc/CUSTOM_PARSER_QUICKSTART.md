# 自定义解析器快速开始

## 5分钟快速集成指南

### 步骤1: 添加依赖（pom.xml）

```xml
<dependency>
    <groupId>cn.qaiu</groupId>
    <artifactId>parser</artifactId>
    <version>10.1.17</version>
</dependency>
```

### 步骤2: 实现解析器（3个文件）

#### 2.1 创建解析工具类 `MyPanTool.java`

```java
package com.example.myapp.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class MyPanTool implements IPanTool {
    private final ShareLinkInfo shareLinkInfo;
    
    // 必须有这个构造器！
    public MyPanTool(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }
    
    @Override
    public Future<String> parse() {
        Promise<String> promise = Promise.promise();
        
        String shareKey = shareLinkInfo.getShareKey();
        String password = shareLinkInfo.getSharePassword();
        
        // TODO: 调用你的网盘API
        String downloadUrl = "https://mypan.com/download/" + shareKey;
        
        promise.complete(downloadUrl);
        return promise.future();
    }
}
```

#### 2.2 创建注册器 `ParserRegistry.java`

```java
package com.example.myapp.config;

import cn.qaiu.parser.CustomParserConfig;
import cn.qaiu.parser.CustomParserRegistry;
import com.example.myapp.parser.MyPanTool;

public class ParserRegistry {
    
    public static void init() {
        CustomParserConfig config = CustomParserConfig.builder()
                .type("mypan")              // 唯一标识
                .displayName("我的网盘")     // 显示名称
                .toolClass(MyPanTool.class) // 解析器类
                .build();
        
        CustomParserRegistry.register(config);
    }
}
```

#### 2.3 在应用启动时注册

```java
package com.example.myapp;

import com.example.myapp.config.ParserRegistry;
import io.vertx.core.Vertx;
import cn.qaiu.WebClientVertxInit;

public class Application {
    
    public static void main(String[] args) {
        // 1. 初始化 Vertx（必需）
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 2. 注册自定义解析器
        ParserRegistry.init();
        
        // 3. 启动应用...
        System.out.println("应用启动成功！");
    }
}
```

### 步骤3: 使用解析器

```java
package com.example.myapp.service;

import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.IPanTool;

public class DownloadService {
    
    public String getDownloadUrl(String shareKey, String password) {
        // 创建解析器
        IPanTool tool = ParserCreate.fromType("mypan")
                .shareKey(shareKey)
                .setShareLinkInfoPwd(password)
                .createTool();
        
        // 同步解析
        return tool.parseSync();
        
        // 或异步解析：
        // tool.parse().onSuccess(url -> {
        //     System.out.println("下载链接: " + url);
        // });
    }
}
```

## 完整示例（可直接运行）

```java
package com.example;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.CustomParserConfig;
import cn.qaiu.parser.CustomParserRegistry;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.WebClientVertxInit;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class QuickStartExample {
    
    public static void main(String[] args) {
        // 1. 初始化环境
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        
        // 2. 注册自定义解析器
        CustomParserConfig config = CustomParserConfig.builder()
                .type("demo")
                .displayName("演示网盘")
                .toolClass(DemoPanTool.class)
                .build();
        CustomParserRegistry.register(config);
        System.out.println("✓ 解析器注册成功");
        
        // 3. 使用解析器
        IPanTool tool = ParserCreate.fromType("demo")
                .shareKey("test123")
                .setShareLinkInfoPwd("pass123")
                .createTool();
        
        String url = tool.parseSync();
        System.out.println("✓ 下载链接: " + url);
        
        // 清理
        vertx.close();
    }
    
    // 演示解析器实现
    static class DemoPanTool implements IPanTool {
        private final ShareLinkInfo info;
        
        public DemoPanTool(ShareLinkInfo info) {
            this.info = info;
        }
        
        @Override
        public Future<String> parse() {
            Promise<String> promise = Promise.promise();
            String url = "https://demo.com/download/" 
                       + info.getShareKey() 
                       + "?pwd=" + info.getSharePassword();
            promise.complete(url);
            return promise.future();
        }
    }
}
```

运行输出：
```
✓ 解析器注册成功
✓ 下载链接: https://demo.com/download/test123?pwd=pass123
```

## 常见问题速查

### Q: 忘记注册解析器会怎样？
A: 抛出异常：`未找到类型为 'xxx' 的解析器`

**解决方法：** 确保在使用前调用 `CustomParserRegistry.register(config)`

### Q: 构造器写错了会怎样？
A: 抛出异常：`toolClass必须有ShareLinkInfo单参构造器`

**解决方法：** 确保有这个构造器：
```java
public MyTool(ShareLinkInfo info) { ... }
```

### Q: 可以从分享链接自动识别吗？
A: 不可以。自定义解析器只能通过 `fromType` 创建。

**正确用法：**
```java
ParserCreate.fromType("mypan")  // ✓ 正确
    .shareKey("abc")
    .createTool();

ParserCreate.fromShareUrl("https://...")  // ✗ 不支持
```

### Q: 如何调试解析器？
A: 在 `parse()` 方法中添加日志：

```java
@Override
public Future<String> parse() {
    System.out.println("开始解析: " + shareLinkInfo);
    // ... 解析逻辑
}
```

## Spring Boot 集成示例

```java
@Configuration
public class ParserConfig {
    
    @Bean
    public Vertx vertx() {
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);
        return vertx;
    }
    
    @PostConstruct
    public void registerCustomParsers() {
        CustomParserConfig config = CustomParserConfig.builder()
                .type("mypan")
                .displayName("我的网盘")
                .toolClass(MyPanTool.class)
                .build();
        
        CustomParserRegistry.register(config);
        log.info("自定义解析器注册完成");
    }
}
```

## 下一步

- 📖 阅读[完整文档](CUSTOM_PARSER_GUIDE.md)了解高级用法
- 🔍 查看[测试代码](../src/test/java/cn/qaiu/parser/CustomParserTest.java)了解更多示例
- 💡 参考[内置解析器](../src/main/java/cn/qaiu/parser/impl/)了解最佳实践

## 技术支持

遇到问题？
1. 查看[完整文档](CUSTOM_PARSER_GUIDE.md)
2. 查看[测试用例](../src/test/java/cn/qaiu/parser/CustomParserTest.java)
3. 提交 [Issue](https://github.com/qaiu/netdisk-fast-download/issues)

