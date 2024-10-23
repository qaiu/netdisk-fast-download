package cn.qaiu.parser;

import cn.qaiu.parser.impl.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 枚举类 PanDomainTemplate 定义了不同网盘服务的模板信息，包括：
 * <ul>
 *     <li>displayName: 网盘服务的显示名称，用于用户界面展示。</li>
 *     <li>regexPattern: 用于匹配和解析分享链接的正则表达式。</li>
 *     <li>standardUrlTemplate: 网盘服务的标准URL模板，用于规范化分享链接。</li>
 *     <li>toolClass: 网盘解析工具实现类。</li>
 * </ul>
 * 请注意：增添网盘时，保证正则表达式最后一个捕捉组能匹配到分享key
 * @author <a href="https://qaiu.top">QAIU</a>
 * at 2023/6/13 4:26
 */
public enum PanDomainTemplate {

    // 网盘定义
    LZ("蓝奏云",
            "https://([a-z0-9-]+)?\\.?lanzou[a-z]\\.com/(.+/)?(.+)",
            "https://lanzoux.com/{shareKey}",
            LzTool.class),

    // https://www.feijix.com/s/
    // https://share.feijipan.com/s/
    FJ("小飞机网盘",
            "https://(share\\.feijipan\\.com|www\\.feijix\\.com)/s/(.+)",
            "https://www.feijix.com/s/{shareKey}",
            FjTool.class),

    // https://lecloud.lenovo.com/share/
    LE("联想乐云",
            "https://lecloud?\\.lenovo\\.com/share/(.+)",
            "https://lecloud.lenovo.com/share/{shareKey}",
            LeTool.class),

    // https://v2.fangcloud.com/s/
    FC("亿方云",
            "https://v2\\.fangcloud\\.(com|cn)/(s|sharing)/([^/]+)",
            "https://v2.fangcloud.com/s/{shareKey}",
            FcTool.class),
    // https://www.ilanzou.com/s/
    IZ("蓝奏云优享",
            "https://www\\.ilanzou\\.com/s/(.+)",
            "https://www.ilanzou.com/s/{shareKey}",
            IzTool.class),
    // https://wx.mail.qq.com/ftn/download?
    QQ("QQ邮箱中转站",
            "https://i?wx\\.mail\\.qq\\.com/ftn/download\\?(.+)",
            "https://iwx.mail.qq.com/ftn/download/{shareKey}",
            QQTool.class),
    // https://f.ws59.cn/f/或者https://www.wenshushu.cn/f/
    WS("文叔叔",
            "https://(f\\.ws([0-9]{2})\\.cn|www\\.wenshushu\\.cn)/f/(.+)",
            "https://www.wenshushu.cn/f/{shareKey}",
            WsTool.class),
    // https://www.123pan.com/s/
    YE("123网盘",
            "https://www\\.(123pan|123865|123684)\\.com/s/(.+)",
            "https://www.123pan.com/s/{shareKey}",
            YeTool.class),
    // https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={code}&isShare=1
    EC("移动云空间",
            "https://www\\.ecpan\\.cn/web(/%23|/#)?/yunpanProxy\\?path=.*&data=" +
                    "([^&]+)&isShare=1",
            "https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={shareKey}&isShare=1",
            EcTool.class),
    // https://cowtransfer.com/s/
    COW("奶牛快传",
            "https://(.*)cowtransfer\\.com/s/(.+)",
            "https://cowtransfer.com/s/{shareKey}",
            CowTool.class),
    CT("城通网盘",
            "https://474b\\.com/file/(.+)",
            "https://474b.com/file/{shareKey}",
            CtTool.class),

    // =====================音乐类解析 分享链接标志->MxxS (单歌曲/普通音质)==========================
    // http://163cn.tv/xxx
    MNES("网易云音乐分享",
            "http(s)?://163cn\\.tv/(.+)",
            "http://163cn.tv/{shareKey}",
            MnesTool.class),
    MNE("网易云音乐",
            "https://music\\.163\\.com/(#/)?song\\?id=(.+)",
            "https://music.163.com/#/song?id={shareKey}",
            MnesTool.MneTool.class),
    // https://c6.y.qq.com/base/fcgi-bin/u?__=xxx
    MQQS("QQ音乐分享",
            "https://(.+)\\.y\\.qq\\.com/base/fcgi-bin/u\\?__=(.+)",
            "https://c6.y.qq.com/base/fcgi-bin/u?__={shareKey}",
            MqqTool.class),
    // https://y.qq.com/n/ryqq/songDetail/000XjcLg0fbRjv?songtype=0
    MQQ("QQ音乐",
            "https://y\\.qq\\.com/n/ryqq/songDetail/(.+)\\?.*",
            "https://y.qq.com/n/ryqq/songDetail/{shareKey}",
            MqqTool.class),

    // https://t1.kugou.com/song.html?id=xxx
    MKGS("酷狗音乐分享",
            "https://(.+)\\.kugou\\.com/song\\.html\\?id=(.+)",
            "https://t1.kugou.com/song.html?id={shareKey}",
            MkgsTool.class),
    // https://www.kugou.com/share/2bi8Fe9CSV3.html?id=2bi8Fe9CSV3#6ed9gna4"
    MKGS2("酷狗音乐分享2",
            "https://www\\.kugou\\.com/share/(.+).html\\?.*",
            "https://www.kugou.com/share/{shareKey}.html",
            MkgsTool.Mkgs2Tool.class),
    // https://www.kugou.com/mixsong/2bi8Fe9CSV3
    MKG("酷狗音乐",
            "https://(.+)\\.kugou\\.com/song\\.html\\?id=(.+)",
            "https://www.kugou.com/mixsong/{shareKey}",
            MkgsTool.MkgTool.class),
    //
    MKWS("酷我音乐分享*",
            "https://(.+)\\.kugou\\.com/song\\.html\\?id=(.+)",
            "https://t1.kugou.com/song.html?id={shareKey}",
            MkwTool.class),
    //
    MMGS("咪咕音乐分享",
            "https://(.+)\\.kugou\\.com/song\\.html\\?id=(.+)",
            "https://t1.kugou.com/song.html?id={shareKey}",
            MkwTool.class),
    // =====================私有盘解析==========================
    // https://pan.huang1111.cn/s/xxx
    // 通用域名([a-z0-9]+(-[a-z0-9]+)*\.)+[a-z]{2,}
    CE("Cloudreve",
               "https://([a-z0-9]+(-[a-z0-9]+)*\\.)+[a-z]{2,}/s/(.+)",
               "https://{CloudreveDomainName}/s/{shareKey}",
       CeTool.class);


    // 网盘的显示名称，用于用户界面显示
    private final String displayName;

    // 用于匹配和解析分享链接的正则表达式，保证最后一个捕捉组能匹配到分享key
    private final String regexPattern;

    // 网盘的标准链接模板，不含占位符，用于规范化分享链接
    private final String standardUrlTemplate;

    // 指向解析工具IPanTool实现类
    private final Class<? extends IPanTool> toolClass;

    PanDomainTemplate(String displayName, String regexPattern, String standardUrlTemplate,
                      Class<? extends IPanTool> toolClass) {
        this.displayName = displayName;
        this.regexPattern = regexPattern;
        this.standardUrlTemplate = standardUrlTemplate;
        this.toolClass = toolClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public String getStandardUrlTemplate() {
        return standardUrlTemplate;
    }

    public Class<? extends IPanTool> getToolClass() {
        return toolClass;
    }

    public static void main(String[] args) {
        // 校验重复
        Set<String> collect =
                Arrays.stream(PanDomainTemplate.values()).map(PanDomainTemplate::getRegexPattern).collect(Collectors.toSet());
        if (collect.size()<PanDomainTemplate.values().length) {
            System.out.println("有重复枚举正则");
        }
        Set<String> collect2 =
                Arrays.stream(PanDomainTemplate.values()).map(PanDomainTemplate::getStandardUrlTemplate).collect(Collectors.toSet());
        if (collect2.size()<PanDomainTemplate.values().length) {
            System.out.println("有重复枚举标准链接");
        }
        System.out.println(collect);
        System.out.println(collect2);


    }
}
