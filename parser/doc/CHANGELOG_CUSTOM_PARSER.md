# 自定义解析器扩展功能更新日志

## 版本：10.1.17+
**更新日期：** 2024-10-17

---

## 🎉 新增功能：自定义解析器扩展

### 概述
用户在依赖本项目 Maven 坐标后，可以自己实现解析器接口，并通过注册机制将自定义解析器集成到系统中。

### 核心变更

#### 1. 新增类

##### CustomParserConfig.java
- **位置：** `cn.qaiu.parser.CustomParserConfig`
- **功能：** 自定义解析器配置类
- **主要字段：**
  - `type`: 解析器类型标识（唯一，必填）
  - `displayName`: 显示名称（必填）
  - `toolClass`: 解析工具类（必填，必须实现IPanTool接口）
  - `standardUrlTemplate`: 标准URL模板（可选）
  - `panDomain`: 网盘域名（可选）
- **使用方式：** 通过 Builder 模式构建
- **验证机制：**
  - 自动验证 toolClass 是否实现 IPanTool 接口
  - 自动验证 toolClass 是否有 ShareLinkInfo 单参构造器
  - 验证必填字段是否为空

##### CustomParserRegistry.java
- **位置：** `cn.qaiu.parser.CustomParserRegistry`
- **功能：** 自定义解析器注册中心
- **主要方法：**
  - `register(CustomParserConfig)`: 注册解析器
  - `unregister(String type)`: 注销解析器
  - `get(String type)`: 获取解析器配置
  - `contains(String type)`: 检查是否已注册
  - `clear()`: 清空所有注册
  - `size()`: 获取注册数量
  - `getAll()`: 获取所有配置
- **特性：**
  - 线程安全（使用 ConcurrentHashMap）
  - 自动检查类型冲突（与内置解析器）
  - 防止重复注册

#### 2. 修改的类

##### ParserCreate.java
- **新增字段：**
  - `customParserConfig`: 自定义解析器配置
  - `isCustomParser`: 是否为自定义解析器标识
  
- **新增构造器：**
  - `ParserCreate(CustomParserConfig, ShareLinkInfo)`: 自定义解析器专用构造器

- **修改的方法：**
  - `fromType(String type)`: 优先查找自定义解析器，再查找内置解析器
  - `createTool()`: 支持创建自定义解析器工具实例
  - `normalizeShareLink()`: 自定义解析器抛出不支持异常
  - `shareKey(String)`: 支持自定义解析器的 shareKey 设置
  - `getStandardUrlTemplate()`: 支持返回自定义解析器的模板
  - `genPathSuffix()`: 支持生成自定义解析器的路径

- **新增方法：**
  - `isCustomParser()`: 判断是否为自定义解析器
  - `getCustomParserConfig()`: 获取自定义解析器配置
  - `getPanDomainTemplate()`: 获取内置解析器模板

#### 3. 测试类

##### CustomParserTest.java
- **位置：** `cn.qaiu.parser.CustomParserTest`
- **测试覆盖：**
  - ✅ 注册自定义解析器
  - ✅ 重复注册检测
  - ✅ 与内置类型冲突检测
  - ✅ 注销解析器
  - ✅ 创建工具实例
  - ✅ fromShareUrl 不支持自定义解析器
  - ✅ normalizeShareLink 不支持
  - ✅ 生成路径后缀
  - ✅ 配置验证
  - ✅ 工具类验证

#### 4. 文档

##### CUSTOM_PARSER_GUIDE.md
- **位置：** `parser/doc/CUSTOM_PARSER_GUIDE.md`
- **内容：** 完整的自定义解析器扩展指南
  - 使用步骤
  - API 参考
  - 完整示例
  - 常见问题

##### CUSTOM_PARSER_QUICKSTART.md
- **位置：** `parser/doc/CUSTOM_PARSER_QUICKSTART.md`
- **内容：** 5分钟快速开始指南
  - 快速集成步骤
  - 可运行示例
  - Spring Boot 集成
  - 常见问题速查

##### README.md（更新）
- **位置：** `parser/README.md`
- **更新内容：**
  - 新增自定义解析器扩展章节
  - 添加快速示例
  - 更新核心 API 列表
  - 添加文档链接

---

## 🔒 设计约束

### 1. 创建限制
**自定义解析器只能通过 `fromType` 方法创建**

```java
// ✅ 支持
ParserCreate.fromType("mypan")
    .shareKey("abc123")
    .createTool();

// ❌ 不支持
ParserCreate.fromShareUrl("https://mypan.com/s/abc123");
```

**原因：** 自定义解析器没有正则表达式来匹配分享链接

### 2. 方法限制
自定义解析器不支持 `normalizeShareLink()` 方法

```java
ParserCreate parser = ParserCreate.fromType("mypan");
parser.normalizeShareLink();  // ❌ 抛出 UnsupportedOperationException
```

### 3. 类型唯一性
- 自定义解析器类型不能与内置类型冲突
- 不能重复注册相同类型

### 4. 构造器要求
解析器工具类必须提供 `ShareLinkInfo` 单参构造器：

```java
public class MyTool implements IPanTool {
    public MyTool(ShareLinkInfo info) {  // 必须
        // ...
    }
}
```

---

## 💡 使用场景

### 1. 企业内部网盘
为企业内部网盘系统添加解析支持

### 2. 私有部署网盘
支持私有部署的网盘服务（如 Cloudreve、可道云的自定义实例）

### 3. 新兴网盘服务
快速支持新出现的网盘服务，无需等待官方更新

### 4. 临时解析方案
在等待官方支持期间的临时解决方案

---

## 📦 影响范围

### 兼容性
- ✅ **向后兼容**：不影响现有功能
- ✅ **可选功能**：不使用则无影响
- ✅ **独立模块**：与内置解析器解耦

### 依赖关系
- 无新增外部依赖
- 使用已有的 `ShareLinkInfo`、`IPanTool` 等接口

### 性能影响
- 注册查找：O(1) 时间复杂度（HashMap）
- 内存占用：每个注册器约 1KB
- 线程安全：使用 ConcurrentHashMap，无锁竞争

---

## 🚀 升级指南

### 现有用户
无需任何改动，所有现有功能保持不变。

### 新用户
参考文档快速集成：
1. [快速开始](doc/CUSTOM_PARSER_QUICKSTART.md)
2. [完整指南](doc/CUSTOM_PARSER_GUIDE.md)

---

## 📝 示例代码

### 最小示例（3步）

```java
// 1. 实现接口
class MyTool implements IPanTool {
    public MyTool(ShareLinkInfo info) {}
    public Future<String> parse() { /* ... */ }
}

// 2. 注册
CustomParserRegistry.register(
    CustomParserConfig.builder()
        .type("mypan")
        .displayName("我的网盘")
        .toolClass(MyTool.class)
        .build()
);

// 3. 使用
IPanTool tool = ParserCreate.fromType("mypan")
    .shareKey("abc")
    .createTool();
String url = tool.parseSync();
```

---

## 🎯 下一步计划

### 潜在增强
- [ ] 支持解析器优先级
- [ ] 支持解析器热更新
- [ ] 添加解析器性能监控
- [ ] 提供解析器开发脚手架

### 社区贡献
欢迎提交优秀的自定义解析器实现，我们将评估后合并到内置解析器中。

---

## 🤝 贡献者
- [@qaiu](https://github.com/qaiu) - 设计与实现

## 📄 许可
MIT License

---

**完整文档：**
- [自定义解析器扩展指南](doc/CUSTOM_PARSER_GUIDE.md)
- [快速开始指南](doc/CUSTOM_PARSER_QUICKSTART.md)
- [测试用例](src/test/java/cn/qaiu/parser/CustomParserTest.java)

