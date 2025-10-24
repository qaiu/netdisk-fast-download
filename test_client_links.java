import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.clientlink.ClientLinkGeneratorFactory;
import cn.qaiu.parser.clientlink.DownloadLinkMeta;
import java.util.Map;

public class TestClientLinks {
    public static void main(String[] args) {
        // 创建一个测试用的 ShareLinkInfo，模拟解析器没有实现客户端下载文件元数据的情况
        ShareLinkInfo shareLinkInfo = ShareLinkInfo.newBuilder()
                .shareUrl("https://example.com/share/test123")
                .panName("测试网盘")
                .type("test")
                .build();
        
        // 添加文件名信息（模拟解析器只解析了文件名）
        shareLinkInfo.getOtherParam().put("fileInfo", new cn.qaiu.entity.FileInfo() {
            @Override
            public String getFileName() {
                return "test-file.zip";
            }
        });
        
        // 测试 DownloadLinkMeta.fromShareLinkInfo() 方法
        DownloadLinkMeta meta = DownloadLinkMeta.fromShareLinkInfo(shareLinkInfo);
        System.out.println("DownloadLinkMeta: " + meta);
        System.out.println("Has valid URL: " + meta.hasValidUrl());
        
        // 测试生成客户端链接
        Map<cn.qaiu.parser.clientlink.ClientLinkType, String> clientLinks = 
            ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
        
        System.out.println("Generated client links count: " + clientLinks.size());
        for (Map.Entry<cn.qaiu.parser.clientlink.ClientLinkType, String> entry : clientLinks.entrySet()) {
            System.out.println(entry.getKey().getDisplayName() + ": " + entry.getValue());
        }
    }
}
