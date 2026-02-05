package cn.qaiu.parser.integration;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.parser.IPanTool;

/**
 * 测试链接识别问题
 * 验证 https://pan.quark.cn/s/30e3c602ac09 是否被正确识别为夸克网盘
 */
public class LinkIdentifyTest {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  链接识别测试");
        System.out.println("========================================\n");
        
        // 测试夸克链接
        testQkLink();
        
        // 测试UC链接
        testUcLink();
        
        System.out.println("\n========================================");
        System.out.println("  测试完成");
        System.out.println("========================================");
    }
    
    private static void testQkLink() {
        System.out.println("=== 测试夸克网盘链接识别 ===\n");
        
        String url = "https://pan.quark.cn/s/30e3c602ac09";
        System.out.println("测试URL: " + url);
        
        try {
            ParserCreate parserCreate = ParserCreate.fromShareUrl(url);
            ShareLinkInfo info = parserCreate.getShareLinkInfo();
            
            System.out.println("识别结果:");
            System.out.println("  网盘名称: " + info.getPanName());
            System.out.println("  网盘类型: " + info.getType());
            System.out.println("  分享KEY: " + info.getShareKey());
            System.out.println("  标准URL: " + info.getStandardUrl());
            
            if ("qk".equalsIgnoreCase(info.getType())) {
                System.out.println("\n✅ 链接正确识别为夸克网盘");
            } else {
                System.out.println("\n❌ 链接识别错误! 期望: qk, 实际: " + info.getType());
            }
        } catch (Exception e) {
            System.out.println("\n❌ 识别失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    private static void testUcLink() {
        System.out.println("=== 测试UC网盘链接识别 ===\n");
        
        String url = "https://drive.uc.cn/s/e623b6da278e4";
        System.out.println("测试URL: " + url);
        
        try {
            ParserCreate parserCreate = ParserCreate.fromShareUrl(url);
            ShareLinkInfo info = parserCreate.getShareLinkInfo();
            
            System.out.println("识别结果:");
            System.out.println("  网盘名称: " + info.getPanName());
            System.out.println("  网盘类型: " + info.getType());
            System.out.println("  分享KEY: " + info.getShareKey());
            System.out.println("  标准URL: " + info.getStandardUrl());
            
            if ("uc".equalsIgnoreCase(info.getType())) {
                System.out.println("\n✅ 链接正确识别为UC网盘");
            } else {
                System.out.println("\n❌ 链接识别错误! 期望: uc, 实际: " + info.getType());
            }
        } catch (Exception e) {
            System.out.println("\n❌ 识别失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
}
