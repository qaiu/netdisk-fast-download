# parser 开发文档

面向开发者的解析器实现说明：约定、数据映射、HTTP 调试与示例代码。

- 语言/构建：Java 17 / Maven
- 关键接口：cn.qaiu.parser.IPanTool（返回 Future<List<FileInfo>>），各站点位于 parser/src/main/java/cn/qaiu/parser/impl
- 数据模型：cn.qaiu.entity.FileInfo（统一对外文件项）

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

        // 3) 异步 -> 同步等待，获取文件列表
        List<FileInfo> files = tool.parseFileList()
                .toCompletionStage().toCompletableFuture().join();
        for (FileInfo f : files) {
            System.out.printf("%s\t%s\t%s\n",
                f.getFileName(), f.getSizeStr(), f.getParserUrl());
        }

        // 4) 原始解析输出（不同盘实现差异较大，仅供调试）
        String raw = tool.parseSync();
        System.out.println("raw: " + (raw == null ? "null" : raw.substring(0, Math.min(raw.length(), 200)) + "..."));

        // 5) 生成 parser 短链 path（可用于上层路由聚合显示）
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
// 获取文件列表
List<FileInfo> files = tool.parseFileList().toCompletionStage().toCompletableFuture().join();
```

要点：
- 必须先 WebClientVertxInit.init(Vertx)；若未显式初始化，内部将懒加载 Vertx.vertx()，建议显式注入以统一生命周期。
- parseFileList 返回 Future<List<FileInfo>>，可直接 join/await；parseSync 仅针对 parse() 的 String 结果。
- 生成短链 path：ParserCreate.genPathSuffix()（用于页面/服务端聚合）。

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

## 2. 文件列表解析规范（按给定 JSON）
目标 JSON（摘要）：
- 列表路径：data.data[]
- 每项结构：item.data（含 attributes、id、type、relationships）
- type："file" 或 "folder"

字段映射建议：
- 通用
  - fileId ← data.id
  - createTime ← data.attributes.created_at（若格式不一致，上层再统一格式化）
  - updateTime ← data.attributes.updated_at
  - fileType：
    - 对文件用 data.attributes.mimetype 或固定 "file"
    - 对目录固定 "folder"
- 文件（type="file"）
  - fileName ← 优先 attributes.basename（示例："GBT+28448-2019.pdf"），无则用 attributes.name
  - sizeStr ← attributes.filesize（示例："18MB"）
  - size ← 尝试用 FileSizeConverter.convertToBytes(sizeStr)，失败则置空
  - parserUrl ← attributes.file_url（示例：BilPan://downLoad?id=...）
  - filePath/parentId ← relationships.parent.data.id（可放到 extParameters.parentId）
  - previewUrl/thumbnail ← attributes.thumbnail（可选）
- 目录（type="folder"）
  - fileName ← attributes.name
  - size/sizeStr ← 置空
  - 统计字段（如 items/trashed_items）可入 extParameters

边界与兼容：
- attributes.filesize 可能为空或为非标准字符串；转换失败时保留 sizeStr，忽略 size。
- attributes.file_url 可能为占位协议（BilPan://），直链转换在下载阶段处理。
- relationships.* 可能为空，读取前需判空。

伪代码（parseFileList 核心片段）：
```java
// 仅示意，按项目 Json 工具替换
JsonObject root = ...; // 接口返回
JsonArray arr = root.getJsonObject("data").getJsonArray("data");
List<FileInfo> list = new ArrayList<>();
for (JsonObject wrap : arr) {
  JsonObject d = wrap.getJsonObject("data");
  String type = d.getString("type");
  JsonObject attrs = d.getJsonObject("attributes");
  FileInfo fi = new FileInfo();
  fi.setFileId(d.getString("id"));
  fi.setCreateTime(attrs.getString("created_at"));
  fi.setUpdateTime(attrs.getString("updated_at"));
  if ("file".equals(type)) {
    String basename = attrs.getString("basename");
    fi.setFileName(basename != null ? basename : attrs.getString("name"));
    fi.setFileType(attrs.getString("mimetype", "file"));
    String sizeStr = attrs.getString("filesize");
    fi.setSizeStr(sizeStr);
    try { if (sizeStr != null) fi.setSize(FileSizeConverter.convertToBytes(sizeStr)); } catch (Exception ignore) {}
    fi.setParserUrl(attrs.getString("file_url"));
    // parentId（可选）
    JsonObject rel = d.getJsonObject("relationships");
    if (rel != null) {
      JsonObject p = rel.getJsonObject("parent");
      if (p != null && p.getJsonObject("data") != null) {
        String pid = p.getJsonObject("data").getString("id");
        Map<String,Object> ext = new HashMap<>();
        ext.put("parentId", pid);
        fi.setExtParameters(ext);
      }
    }
  } else {
    fi.setFileName(attrs.getString("name"));
    fi.setFileType("folder");
  }
  list.add(fi);
}
return Future.succeededFuture(list);
```

---

## 3. curl 转 Java 11 HttpClient 示例
以 GET 为例（来源：developer-oss.lanrar.com）：
```java
HttpClient client = HttpClient.newHttpClient();
String q = "<替换为长查询串>";
String url = "https://developer-oss.lanrar.com/file/?" + URLEncoder.encode(q, StandardCharsets.UTF_8);
HttpRequest req = HttpRequest.newBuilder(URI.create(url))
    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
    .header("accept-language", "zh-CN,zh;q=0.9")
    .header("cache-control", "max-age=0")
    .header("dnt", "1")
    .header("priority", "u=0, i")
    .header("referer", "https://developer-oss.lanrar.com/file/?" + q)
    .header("sec-ch-ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Microsoft Edge\";v=\"140\"")
    .header("sec-ch-ua-mobile", "?0")
    .header("sec-ch-ua-platform", "\"macOS\"")
    .header("sec-fetch-dest", "document")
    .header("sec-fetch-mode", "navigate")
    .header("sec-fetch-site", "same-origin")
    .header("upgrade-insecure-requests", "1")
    .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0")
    .header("Cookie", "acw_tc=<acw_tc>; cdn_sec_tc=<cdn_sec_tc>; acw_sc__v2=<acw_sc__v2>")
    .GET()
    .build();
HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
System.out.println(resp.statusCode());
System.out.println(resp.body());
```

POST 示例（来源：Weiyun Share BatchDownload，使用 JSON）：
```java
HttpClient client = HttpClient.newHttpClient();
String url = "https://share.weiyun.com/webapp/json/weiyunShare/WeiyunShareBatchDownload?refer=chrome_mac&g_tk=1399845656&r=0.3925692266635241";
String json = "{...与 curl/requests 等价 JSON 负载，使用占位参数...}";
HttpRequest req = HttpRequest.newBuilder(URI.create(url))
    .header("accept", "application/json, text/plain, */*")
    .header("content-type", "application/json;charset=UTF-8")
    .header("origin", "https://share.weiyun.com")
    .header("referer", "https://share.weiyun.com/<shareKey>")
    .header("user-agent", "Mozilla/5.0 ...")
    .header("Cookie", "uin=<uin>; skey=<skey>; p_skey=<p_skey>; ...")
    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
    .build();
HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
```
提示：
- Cookie/Token 使用占位并从外部注入，避免硬编码与泄露。
- r/g_tk 等参数如需计算，请在实现类中封装。

---

## 4. IntelliJ IDEA `.http` 调试样例
保存为 `requests.http`，可配合环境变量使用。

GET：
```http
### 开发者资源 GET 示例
GET https://developer-oss.lanrar.com/file/?{{q}}
accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
accept-language: zh-CN,zh;q=0.9
cache-control: max-age=0
dnt: 1
priority: u=0, i
referer: https://developer-oss.lanrar.com/file/?{{q}}
sec-ch-ua: "Chromium";v="140", "Not=A?Brand";v="24", "Microsoft Edge";v="140"
sec-ch-ua-mobile: ?0
sec-ch-ua-platform: "macOS"
sec-fetch-dest: document
sec-fetch-mode: navigate
sec-fetch-site: same-origin
upgrade-insecure-requests: 1
user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0
Cookie: acw_tc={{acw_tc}}; cdn_sec_tc={{cdn_sec_tc}}; acw_sc__v2={{acw_sc_v2}}

> {% client.log("status: " + response.status); %}

### 环境变量（可在 HTTP Client Environment 中配置）
@q=替换为实际长查询串
@acw_tc=your_acw_tc
@cdn_sec_tc=your_cdn_sec_tc
@acw_sc_v2=your_acw_sc__v2
```

POST：
```http
### Weiyun 批量下载 POST 示例
POST https://share.weiyun.com/webapp/json/weiyunShare/WeiyunShareBatchDownload?refer=chrome_mac&g_tk={{g_tk}}&r={{r}}
accept: application/json, text/plain, */*
content-type: application/json;charset=UTF-8
origin: https://share.weiyun.com
referer: https://share.weiyun.com/{{share_key}}
user-agent: Mozilla/5.0 ...
Cookie: uin={{uin}}; skey={{skey}}; p_skey={{p_skey}}; p_uin={{p_uin}}; wyctoken={{wyctoken}}

{
  "req_header": "{...}",
  "req_body": "{...}"
}
```

---

## 5. 开发流程建议
- 新增站点：在 impl 下新增 Tool，实现 IPanTool，复用 PanBase/模板类；补充单测。
- 字段不全：尽量回填 sizeStr/createTime 等便于前端展示；不可用字段置空。
- 单测：放置于 parser/src/test/java，尽量添加 1-2 个 happy path + 1 个边界用例。

## 6. 常见问题
- 容量解析失败：保留 sizeStr，并忽略 size；避免抛出异常影响整体列表。
- 协议占位下载链接：统一放至 parserUrl，直链转换由下载阶段处理。
- 鉴权：Cookie/Token 过期问题由上层刷新或外部注入处理；解析器保持无状态最佳。

---

## 7. 参考
- FileInfo：parser/src/main/java/cn/qaiu/entity/FileInfo.java
- IPanTool：parser/src/main/java/cn/qaiu/parser/IPanTool.java
- FileSizeConverter：parser/src/main/java/cn/qaiu/util/FileSizeConverter.java
