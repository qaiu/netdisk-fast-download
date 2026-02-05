package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanDomainTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * UC和夸克网盘客户端链接生成测试
 * 测试在有下载链接和请求头的情况下，是否能正确生成下载命令
 */
public class UcQkClientLinkTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  UC/夸克网盘客户端链接生成测试");
        System.out.println("========================================\n");
        
        // 测试 UC 网盘
        testUcClientLinks();
        
        // 测试夸克网盘
        testQkClientLinks();
        
        System.out.println("\n========================================");
        System.out.println("  测试完成");
        System.out.println("========================================");
    }
    
    private static void testUcClientLinks() {
        System.out.println("=== 测试 UC 网盘客户端链接生成 ===\n");
        
        // 创建 ShareLinkInfo (使用 Builder)
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("uc")
                .panName(PanDomainTemplate.UC.getDisplayName())
                .shareKey("test123")
                .build();
        
        // 模拟下载链接（UC网盘的真实下载链接格式）
        String downloadUrl = "https://pc-api.uc.cn/1/clouddrive/file/download?xxx";
        info.getOtherParam().put("downloadUrl", downloadUrl);
        
        // 模拟下载请求头（包含Cookie）
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "__pus=5e2bfe93fc55175482cd81dbafb41586AARGIGToqJ7RFMUETPbInASaHMcrrwTch6A6cjwBQQF0gKWZZxV20iixkInaK3AQrW+zsggDwifeq2BZ6fOBsj1N; __kp=72747319-24ad-44da-85a9-133fedd72818; __kps=AASxYmDMULu4nzmEK/wFzK3I; __ktd=dvy3qySVr8aXEqUuxMJydA==; __uid=AASxYmDMULu4nzmEK/wFzK3I; __puus=bdb2e15d24f1a15fe2b5e108b44f0805AAR498zI4bjrVRD3mNor9LX8YbixADr2C4YebqDb1fvtySVLiF3VgyASPRi/VSfMikDVd3yHUtbqP3ZwAteImXbevPo84hloWgCG0qCouDie3PKBIXq4+UxiXay2GHtst71wVq7ODiWV3OzzazpYgtGqTjep8F4BWtwdwtCjQz6l6OHVYy/LkTe3/6eeAreiRNU=");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", "https://drive.uc.cn/");
        info.getOtherParam().put("downloadHeaders", headers);
        
        // 设置文件信息（通过otherParam）
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName("测试文件.zip");
        info.getOtherParam().put("fileInfo", fileInfo);
        
        // 生成客户端链接
        Map<ClientLinkType, String> clientLinks = ClientLinkGeneratorFactory.generateAll(info);
        
        if (clientLinks.isEmpty()) {
            System.out.println("❌ 未能生成任何客户端链接\n");
            return;
        }
        
        System.out.println("✅ 成功生成 " + clientLinks.size() + " 个客户端链接:\n");
        
        for (Map.Entry<ClientLinkType, String> entry : clientLinks.entrySet()) {
            ClientLinkType type = entry.getKey();
            String link = entry.getValue();
            
            System.out.println("【" + type.getDisplayName() + "】");
            System.out.println(link);
            System.out.println();
            
            // 验证链接格式
            validateLink(type, link, "UC");
        }
    }
    
    private static void testQkClientLinks() {
        System.out.println("=== 测试夸克网盘客户端链接生成 ===\n");
        
        // 创建 ShareLinkInfo (使用 Builder)
        ShareLinkInfo info = ShareLinkInfo.newBuilder()
                .type("qk")
                .panName(PanDomainTemplate.QK.getDisplayName())
                .shareKey("test456")
                .build();
        
        // 模拟下载链接（夸克网盘的真实下载链接格式）
        String downloadUrl = "https://drive-pc.quark.cn/1/clouddrive/file/download?xxx";
        info.getOtherParam().put("downloadUrl", downloadUrl);
        
        // 模拟下载请求头（包含Cookie）
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "__pus=abc123def456; __kp=ghi789jkl012; __kps=mno345pqr678; __ktd=stu901vwx234; __uid=yza567bcd890; __puus=efg123hij456");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", "https://pan.quark.cn/");
        info.getOtherParam().put("downloadHeaders", headers);
        
        // 设置文件信息（通过otherParam）
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName("测试文件.mp4");
        info.getOtherParam().put("fileInfo", fileInfo);
        
        // 生成客户端链接
        Map<ClientLinkType, String> clientLinks = ClientLinkGeneratorFactory.generateAll(info);
        
        if (clientLinks.isEmpty()) {
            System.out.println("❌ 未能生成任何客户端链接\n");
            return;
        }
        
        System.out.println("✅ 成功生成 " + clientLinks.size() + " 个客户端链接:\n");
        
        for (Map.Entry<ClientLinkType, String> entry : clientLinks.entrySet()) {
            ClientLinkType type = entry.getKey();
            String link = entry.getValue();
            
            System.out.println("【" + type.getDisplayName() + "】");
            System.out.println(link);
            System.out.println();
            
            // 验证链接格式
            validateLink(type, link, "夸克");
        }
    }
    
    private static void validateLink(ClientLinkType type, String link, String panName) {
        boolean valid = true;
        StringBuilder issues = new StringBuilder();
        
        switch (type) {
            case CURL:
                if (!link.startsWith("curl ")) {
                    valid = false;
                    issues.append("不是以 'curl ' 开头; ");
                }
                if (!link.contains("--header \"Cookie:")) {
                    valid = false;
                    issues.append("缺少 Cookie 请求头; ");
                }
                if (!link.contains("--output")) {
                    valid = false;
                    issues.append("缺少输出文件名; ");
                }
                break;
                
            case ARIA2:
                if (!link.contains("aria2c")) {
                    valid = false;
                    issues.append("不包含 'aria2c'; ");
                }
                if (!link.contains("--header=\"Cookie:")) {
                    valid = false;
                    issues.append("缺少 Cookie 请求头; ");
                }
                if (!link.contains("--out=")) {
                    valid = false;
                    issues.append("缺少输出文件名; ");
                }
                break;
                
            case THUNDER:
                if (!link.startsWith("thunder://")) {
                    valid = false;
                    issues.append("不是以 'thunder://' 开头; ");
                }
                // 迅雷不支持 Cookie，所以不检查
                break;
        }
        
        if (valid) {
            System.out.println("  ✓ " + panName + "的" + type.getDisplayName() + "格式验证通过");
        } else {
            System.out.println("  ⚠️ " + panName + "的" + type.getDisplayName() + "格式异常: " + issues);
        }
    }
}
