# parser

NFD 解析器模块：聚合各类网盘/分享页解析，统一输出文件列表与下载信息，供上层下载器使用。

- 语言：Java 17
- 构建：Maven
- 模块版本：10.1.9

## 依赖（Maven Central）
- Maven（无需额外仓库配置）：
```xml
<dependency>
  <groupId>cn.qaiu</groupId>
  <artifactId>parser</artifactId>
  <version>10.1.9</version>
</dependency>
```
- Gradle Groovy DSL：
```groovy
dependencies {
  implementation 'cn.qaiu:parser:10.1.9'
}
```
- Gradle Kotlin DSL：
```kotlin
dependencies {
  implementation("cn.qaiu:parser:10.1.9")
}
```

## 核心 API 速览
- WebClientVertxInit：注入/获取 Vert.x 实例（内部 HTTP 客户端依赖）。
- ParserCreate：从分享链接或类型构建解析器；生成短链 path。
- IPanTool：统一解析接口（parse、parseFileList、parseById）。

## 使用示例（极简）
```java
Vertx vx = Vertx.vertx();
WebClientVertxInit.init(vx);
IPanTool tool = ParserCreate.fromShareUrl("https://www.ilanzou.com/s/xxxx").createTool();
List<FileInfo> list = tool.parseFileList().toCompletionStage().toCompletableFuture().join();
```
完整示例与调试脚本见 parser/doc/README.md。

## 快速开始
- 环境：JDK >= 17，Maven >= 3.9
- 构建/安装：
```
mvn -pl parser -am clean package
mvn -pl parser -am install
```
- 测试：
```
mvn -pl parser test
```

## 文档
开发者请阅读 parser/doc/README.md（含解析约定、示例、IDEA `.http` 调试）。

## 目录
- src/main/java/cn/qaiu/entity：通用实体（如 FileInfo）
- src/main/java/cn/qaiu/parser：解析框架 & 各站点实现（impl）
- src/test/java：单测与示例

## 许可证
MIT License
