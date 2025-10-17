package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class Demo {

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
