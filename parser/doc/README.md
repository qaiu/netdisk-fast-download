# parser 开发文档

面向开发者的解析器实现说明：约定、数据映射、HTTP 调试与示例代码。

- 语言/构建：Java 17 / Maven
- 关键接口：cn.qaiu.parser.IPanTool（返回 Future<List<FileInfo>>），各站点位于 parser/src/main/java/cn/qaiu/parser/impl
- 数据模型：cn.qaiu.entity.FileInfo（统一对外文件项）
- JavaScript解析器：支持使用JavaScript编写自定义解析器，位于 parser/src/main/resources/custom-parsers/

---

## 0. 快速调用示例（最小可运行）
```java
import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.ParserCreate;
import io.vertx.core.Vertx;
import java.util.List;

public class ParserQuickStart {
    public static void main(String[] args) {
        // 1) 初始化 Vert.x（parser 内部 WebClient 依赖它）
        Vertx vertx = Vertx.vertx();
        WebClientVertxInit.init(vertx);

        // 2) 从分享链接自动识别网盘类型并创建解析器
        String shareUrl = "https://www.ilanzou.com/s/xxxx"; // 替换为实际分享链接
        IPanTool tool = ParserCreate.fromShareUrl(shareUrl)
                // .setShareLinkInfoPwd("1234") // 如有提取码可设置
                .createTool();

        // 3) 使用同步方法获取文件列表（推荐）
        List<FileInfo> files = tool.parseFileListSync();
        for (FileInfo f : files) {
            System.out.printf("%s\t%s\t%s\n",
                f.getFileName(), f.getSizeStr(), f.getParserUrl());
        }

        // 4) 使用同步方法获取原始解析输出（不同盘实现差异较大，仅供调试）
        String raw = tool.parseSync();
        System.out.println("raw: " + (raw == null ? "null" : raw.substring(0, Math.min(raw.length(), 200)) + "..."));
        
        // 5) 使用同步方法根据文件ID获取下载链接（可选）
        if (!files.isEmpty()) {
            String fileId = files.get(0).getFileId();
            String downloadUrl = tool.parseByIdSync();
            System.out.println("文件下载链接: " + downloadUrl);
        }

        // 6) 生成 parser 短链 path（可用于上层路由聚合显示）
        String path = ParserCreate.fromShareUrl(shareUrl).genPathSuffix();
        System.out.println("path suffix: /" + path);

        vertx.close();
    }
}
```

等价用法：已知网盘类型 + shareKey 构造
```java
IPanTool tool = ParserCreate.fromType("lz") // 对应 PanDomainTemplate.LZ
        .shareKey("abcd12")                   // 必填：分享 key
        .setShareLinkInfoPwd("1234")         // 可选：提取码
        .createTool();
// 获取文件列表（使用同步方法）
List<FileInfo> files = tool.parseFileListSync();
```

要点：
- 必须先 WebClientVertxInit.init(Vertx)；若未显式初始化，内部将懒加载 Vertx.vertx()，建议显式注入以统一生命周期。
- 支持三种同步方法：
  - `parseSync()`: 解析单个文件下载链接
  - `parseFileListSync()`: 解析文件列表
  - `parseByIdSync()`: 根据文件ID获取下载链接
- 异步方法仍可用：parse()、parseFileList()、parseById() 返回 Future 对象
- 生成短链 path：ParserCreate.genPathSuffix()（用于页面/服务端聚合）。

## JavaScript解析器快速开始

除了Java解析器，还支持使用JavaScript编写自定义解析器：

### 1. 创建JavaScript解析器

在 `parser/src/main/resources/custom-parsers/` 目录下创建 `.js` 文件：

```javascript
// ==UserScript==
// @name         我的解析器
// @type         my_parser
// @displayName  我的网盘
// @description  使用JavaScript实现的网盘解析器
// @match        https?://example\.com/s/(?<KEY>\w+)
// @author       yourname
// @version      1.0.0
// ==/UserScript==

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    var response = http.get(url);
    return response.body();
}
```

### 2. JavaScript解析器特性

- **重定向处理**：支持`getNoRedirect()`方法获取302重定向的真实链接
- **代理支持**：自动支持HTTP/SOCKS代理配置
- **类型提示**：提供完整的JSDoc类型定义
- **热加载**：修改后重启应用即可生效

### 3. 详细文档

- [JavaScript解析器开发指南](JAVASCRIPT_PARSER_GUIDE.md)
- [自定义解析器开发指南](CUSTOM_PARSER_GUIDE.md)

---

## 1. 解析器约定
- 输入：目标分享/目录页或接口的上下文（通常在实现类构造或初始化时已注入必要参数，如 shareKey、cookie、headers）。
- 输出：Future<List<FileInfo>>（文件/目录混合列表，必要时区分 file/folder）。
- 错误：失败场景通过 Future 失败或返回空列表；日志由上层统一处理。
- 并发：尽量使用 Vert.x Web Client 异步请求；注意限流与重试策略由实现类自定。

FileInfo 关键字段（节选）：
- fileId：唯一标识
- fileName：展示名（建议带扩展名，如 basename）
- fileType：如 "file"/"folder" 或 mime（实现自定，保持一致即可）
- size（Long, 字节）/ sizeStr（原文字符串）
- createTime / updateTime：格式 yyyy-MM-dd HH:mm:ss（如源为时间戳或 yyyy-MM-dd 需转）
- parserUrl：非直连下载的中间链接或协议占位（如 BilPan://）
- filePath / previewUrl / extParameters：按需补充

工具类：
- FileSizeConverter：字符串容量转字节、字节转可读容量

---

## 2. 文件列表解析规范

### 通用解析原则

1. **数据结构识别**：根据网盘API响应结构确定文件列表的路径
2. **字段映射**：将网盘特定字段映射到统一的`FileInfo`对象
3. **类型区分**：正确识别文件和文件夹类型
4. **数据转换**：处理时间格式、文件大小等数据格式转换

### FileInfo字段映射指南

| FileInfo字段 | 说明 | 映射建议 |
|-------------|------|----------|
| `fileName` | 文件名 | 优先使用文件名字段，无则使用标题字段 |
| `fileId` | 文件ID | 使用网盘提供的唯一标识符 |
| `fileType` | 文件类型 | "file"或"folder" |
| `size` | 文件大小(字节) | 转换为字节数，文件夹可为0 |
| `sizeStr` | 文件大小(可读) | 保持网盘原始格式或转换 |
| `createTime` | 创建时间 | 统一时间格式 |
| `updateTime` | 更新时间 | 统一时间格式 |
| `parserUrl` | 下载链接 | 网盘提供的下载URL |
| `previewUrl` | 预览链接 | 可选，网盘提供的预览URL |

### 常见数据转换

- **文件大小**：使用`FileSizeConverter`进行字符串与字节数转换
- **时间格式**：统一转换为标准时间格式
- **文件类型**：根据网盘API判断文件/文件夹类型

### 解析注意事项

- **数据验证**：检查必要字段是否存在，避免空指针异常
- **格式兼容**：处理不同网盘的数据格式差异
- **错误处理**：转换失败时提供合理的默认值
- **扩展字段**：额外信息可存储在`extParameters`中

### 解析示例

```java
// 通用解析模式示例
JsonObject root = response.json(); // 获取API响应
JsonArray fileList = root.getJsonArray("files"); // 根据实际API调整路径
List<FileInfo> result = new ArrayList<>();

for (JsonObject item : fileList) {
    FileInfo fileInfo = new FileInfo();
    
    // 基本字段映射
    fileInfo.setFileName(item.getString("name"));
    fileInfo.setFileId(item.getString("id"));
    fileInfo.setFileType(item.getString("type").equals("file") ? "file" : "folder");
    
    // 文件大小处理
    String sizeStr = item.getString("size");
    if (sizeStr != null) {
        fileInfo.setSizeStr(sizeStr);
        try {
            fileInfo.setSize(FileSizeConverter.convertToBytes(sizeStr));
        } catch (Exception e) {
            // 转换失败时保持sizeStr，size为0
        }
    }
    
    // 时间处理
    fileInfo.setCreateTime(formatTime(item.getString("createTime")));
    fileInfo.setUpdateTime(formatTime(item.getString("updateTime")));
    
    // 下载链接
    fileInfo.setParserUrl(item.getString("downloadUrl"));
    
    result.add(fileInfo);
}
```

### JavaScript解析器示例

```javascript
function parseFileList(shareLinkInfo, http, logger) {
    var response = http.get(shareLinkInfo.getShareUrl());
    var data = response.json();
    
    var fileList = [];
    var files = data.files || data.data || data.items; // 根据实际API调整
    
    for (var i = 0; i < files.length; i++) {
        var file = files[i];
        var fileInfo = {
            fileName: file.name || file.title,
            fileId: file.id,
            fileType: file.type === "file" ? "file" : "folder",
            size: file.size || 0,
            sizeStr: file.sizeStr || formatSize(file.size),
            createTime: file.createTime,
            updateTime: file.updateTime,
            parserUrl: file.downloadUrl || file.url
        };
        
        fileList.push(fileInfo);
    }
    
    return fileList;
}
```

---

## 3. 开发流程建议
- 新增站点：在 impl 下新增 Tool，实现 IPanTool，复用 PanBase/模板类；补充单测。
- 字段不全：尽量回填 sizeStr/createTime 等便于前端展示；不可用字段置空。
- 单测：放置于 parser/src/test/java，尽量添加 1-2 个 happy path + 1 个边界用例。

## 4. 常见问题
- 容量解析失败：保留 sizeStr，并忽略 size；避免抛出异常影响整体列表。
- 协议占位下载链接：统一放至 parserUrl，直链转换由下载阶段处理。
- 鉴权：Cookie/Token 过期问题由上层刷新或外部注入处理；解析器保持无状态最佳。

---

## 5. 参考
- FileInfo：parser/src/main/java/cn/qaiu/entity/FileInfo.java
- IPanTool：parser/src/main/java/cn/qaiu/parser/IPanTool.java
- FileSizeConverter：parser/src/main/java/cn/qaiu/util/FileSizeConverter.java
