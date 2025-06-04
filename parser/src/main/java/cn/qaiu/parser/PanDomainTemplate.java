package cn.qaiu.parser;

import cn.qaiu.parser.impl.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

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
             compile("https://(?:[a-zA-Z\\d-]+\\.)?((lanzou[a-z])|(lanzn))\\.com/(.+/)?(?<KEY>.+)"),
            "https://lanzoux.com/{shareKey}",
            LzTool.class),

    // https://www.feijix.com/s/
    // https://share.feijipan.com/s/
    FJ("小飞机网盘",
            compile("https://(share\\.feijipan\\.com|www\\.feijix\\.com)/s/(?<KEY>.+)"),
            "https://www.feijix.com/s/{shareKey}",
            FjTool.class),

    // https://lecloud.lenovo.com/share/
    LE("联想乐云",
            compile("https://lecloud?\\.lenovo\\.com/share/(?<KEY>.+)"),
            "https://lecloud.lenovo.com/share/{shareKey}",
            LeTool.class),

    // https://v2.fangcloud.com/s/
    FC("亿方云",
            compile("https://v2\\.fangcloud\\.(com|cn)/(s|sharing)/(?<KEY>.+)"),
            "https://v2.fangcloud.com/s/{shareKey}",
            "https://www.fangcloud.com/",
            FcTool.class),
    // https://www.ilanzou.com/s/
    IZ("蓝奏云优享",
            compile("https://www\\.ilanzou\\.com/s/(?<KEY>.+)"),
            "https://www.ilanzou.com/s/{shareKey}",
            IzTool.class),
    // https://wx.mail.qq.com/ftn/download?
    QQ("QQ邮箱中转站",
            compile("https://i?wx\\.mail\\.qq\\.com/ftn/download\\?(?<KEY>.+)"),
            "https://iwx.mail.qq.com/ftn/download/{shareKey}",
            "https://mail.qq.com",
            QQTool.class),
    // https://wx.mail.qq.com/s?k=uAG9JR42Rqgt010mFp
    QQW("QQ邮箱云盘",
            compile("https://i?wx\\.mail\\.qq\\.com/s\\?k=(?<KEY>.+)"),
            "https://wx.mail.qq.com/s?k={shareKey}",
            "https://mail.qq.com",
            QQwTool.class),
    // https://f.ws59.cn/f/或者https://www.wenshushu.cn/f/
    WS("文叔叔",
            compile("https://(f\\.ws(\\d{2})\\.cn|www\\.wenshushu\\.cn)/f/(?<KEY>.+)"),
            "https://www.wenshushu.cn/f/{shareKey}",
            WsTool.class),
    // https://www.123pan.com/s/
    YE("123网盘",
            compile("https://www\\.(123pan\\.com|123865\\.com|123684\\.com|123912\\.com|123pan\\.cn)/s/(?<KEY>.+)(.html)?"),
            "https://www.123pan.com/s/{shareKey}",
            YeTool.class),
    // https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={code}&isShare=1
    EC("移动云空间",
            compile("https://www\\.ecpan\\.cn/web(/%23|/#)?/yunpanProxy\\?path=.*&data=" +
                    "(?<KEY>[^&]+)&isShare=1"),
            "https://www.ecpan.cn/web/#/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data={shareKey}&isShare=1",
            EcTool.class),
    // https://cowtransfer.com/s/
    COW("奶牛快传",
            compile("https://(.*)cowtransfer\\.com/s/(?<KEY>.+)"),
            "https://cowtransfer.com/s/{shareKey}",
            CowTool.class),
    CT("城通网盘",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?(ctfile|545c|u062|ghpym|474b)\\.com/f(ile)?/" +
                    "(?<KEY>[0-9a-zA-Z_-]+)(\\?p=(?<PWD>\\w+))?"),
            "https://474b.com/file/{shareKey}",
            CtTool.class),
    // https://xxx.118pan.com/bxxx
    P118("118网盘",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?118pan\\.com/b(?<KEY>.+)"),
            "https://qaiu.118pan.com/b{shareKey}",
            P118Tool.class),
    // https://www.vyuyun.com/s/QMa6ie?password=I4KG7H
    // https://www.vyuyun.com/s/QMa6ie/file?password=I4KG7H
    PVYY("微雨云存储",
            compile("https://www\\.vyuyun\\.com/s/(?<KEY>[a-zA-Z\\d-]+)(/file)?(\\?password=(?<PWD>\\w+))?"),
            "https://www.vyuyun.com/s/{shareKey}?password={pwd}",
            PvyyTool.class),
    // https://1drv.ms/w/s!Alg0feQmCv2rnRFd60DQOmMa-Oh_?e=buaRtp
    // https://1drv.ms/u/c/abfd0a26e47d3458/EdYACWvPq85Et797YmvL5LgBruUKoNxqIFATXhIv1PI2_Q?e=z4ffNJ
    POD("OneDrive",
            compile("https://1drv\\.ms/(?<KEY>.+)"),
            "https://1drv\\.ms/{shareKey}",
            "https://onedrive.live.com/",
            PodTool.class),
    // 404网盘 https://drive.google.com/file/d/xxx/view?usp=sharing
    PGD("GoogleDrive",
            compile("https://drive\\.google\\.com/file/d/(?<KEY>.+)/view(\\?usp=(sharing|drive_link))?"),
            "https://drive.google.com/file/d/{shareKey}/view?usp=sharing",
            PgdTool.class),
    // iCloud https://www.icloud.com.cn/iclouddrive/xxx#fonts
    PIC("iCloud",
            compile("https://www\\.icloud\\.com\\.cn/iclouddrive/(?<KEY>[a-z_A-Z\\d-=]+)(#(.+))?"),
            "https://www.icloud.com.cn/iclouddrive/{shareKey}",
            PicTool.class),
    // https://www.dropbox.com/scl/fi/cwnbms1yn8u6rcatzyta7/emqx-5.0.26-el7-amd64.tar.gz?rlkey=3uoi4bxz5mv93jmlaws0nlol1&e=8&st=fe0lclc2&dl=0
    PDB("dropbox",
            compile("https://www.dropbox.com/scl/fi/(?<KEY>\\w+)/.+?rlkey=(?<PWD>\\w+).*"),
            "https://www.dropbox.com/scl/fi/{shareKey}/?rlkey={pwd}&dl=0",
            PdbTool.class),
    P115("115网盘",
            compile("https://(115|anxia).com/s/(?<KEY>\\w+)(\\?password=(?<PWD>\\w+))?([&#].*)?"),
            "https://115.com/s/{shareKey}?password={pwd}",
            P115Tool.class),
    // 链接：https://www.yunpan.com/surl_yD7wz4VgU9v（提取码：fc70）
//    P360("360云盘(需要referer头)",
//            compile("https://www\\.yunpan\\.com/(?<KEY>\\w+)"),
//            "https://www.yunpan.com/{shareKey}",
//            P360Tool.class),

    // https://pan-yz.cldisk.com/external/m/file/953658049102462976
    Pcx("超星云盘(需要referer头)",
            compile("https://pan-yz\\.cldisk\\.com/external/m/file/(?<KEY>\\w+)"),
            "https://pan-yz.cldisk.com/external/m/file/{shareKey}",
            PcxTool.class),
    // =====================音乐类解析 分享链接标志->MxxS (单歌曲/普通音质)==========================
    // http://163cn.tv/xxx
    MNES("网易云音乐分享",
            compile("http(s)?://163cn\\.tv/(?<KEY>.+)"),
            "http://163cn.tv/{shareKey}",
            MnesTool.class),
    // https://music.163.com/#/song?id=xxx
    MNE("网易云音乐歌曲详情",
            compile("https://(y.)?music\\.163\\.com/(#|m/)?song\\?id=(?<KEY>.+)(&.*)?"),
            "https://music.163.com/#/song?id={shareKey}",
            MnesTool.MneTool.class),
    // https://c6.y.qq.com/base/fcgi-bin/u?__=xxx
    MQQS("QQ音乐分享",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?y\\.qq\\.com/base/fcgi-bin/u\\?__=(?<KEY>.+)"),
            "https://c6.y.qq.com/base/fcgi-bin/u?__={shareKey}",
            MqqsTool.class),
    // https://y.qq.com/n/ryqq/songDetail/000XjcLg0fbRjv?songtype=0
    MQQ("QQ音乐歌曲详情",
            compile("https://y\\.qq\\.com/n/ryqq/songDetail/(?<KEY>.+)(\\?.*)?"),
            "https://y.qq.com/n/ryqq/songDetail/{shareKey}",
            MqqsTool.MqqTool.class),

    // https://t1.kugou.com/song.html?id=xxx
    MKGS("酷狗音乐分享",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?kugou\\.com/song\\.html\\?id=(?<KEY>.+)"),
            "https://t1.kugou.com/song.html?id={shareKey}",
            MkgsTool.class),
    // https://www.kugou.com/share/2bi8Fe9CSV3.html?id=2bi8Fe9CSV3#6ed9gna4"
    MKGS2("酷狗音乐分享2",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?kugou\\.com/share/(?<KEY>.+).html.*"),
            "https://www.kugou.com/share/{shareKey}.html",
            MkgsTool.Mkgs2Tool.class),
    // https://www.kugou.com/mixsong/2bi8Fe9CSV3
    MKG("酷狗音乐歌曲详情",
            compile("https://(?:[a-zA-Z\\d-]+\\.)?kugou\\.com/mixsong/(?<KEY>.+)\\.html.*"),
            "https://www.kugou.com/mixsong/{shareKey}.html",
            MkgsTool.MkgTool.class),
    // https://kuwo.cn/play_detail/395500809
    // https://m.kuwo.cn/newh5app/play_detail/318448522
    MKWS("酷我音乐分享*",
            compile("https://(m\\.)?kuwo\\.cn/(newh5app/)?play_detail/(?<KEY>.+)"),
            "https://kuwo.cn/play_detail/{shareKey}",
            MkwTool.class),
    // https://music.migu.cn/v3/music/song/6326951FKBJ?channelId=001002H
    MMGS("咪咕音乐分享",
            compile("https://music\\.migu\\.cn/v3/music/song/(?<KEY>.+)(\\?.*)?"),
            "https://music.migu.cn/v3/music/song/{shareKey}",
            MmgTool.class),
    // =====================私有盘解析==========================

    // Cloudreve自定义域名解析, 解析器CeTool兜底策略, 即任意域名如果匹配不到对应的规则, 则由CeTool统一处理,
    // 如果不属于Cloudreve盘 则调用下一个自定义域名解析器, 若都处理不了则抛出异常, 这种匹配模式类似责任链
    // https://pan.huang1111.cn/s/xxx
    // 通用域名([a-z\\d]+(-[a-z\\d]+)*\.)+[a-z]{2,}
    CE("Cloudreve",
            compile("http(s)?://([a-zA-Z\\d]+(-[a-zA-Z\\d]+)*\\.)+[a-zA-Z]{2,}(/s)?/(?<KEY>.+)"),
            "https://{any}/s/{shareKey}",
            "https://cloudreve.org/",
            CeTool.class),
    // 可道云自定义域名解析
    KD("可道云",
            compile("http(s)?://([a-zA-Z\\d]+(-[a-zA-Z\\d]+)*\\.)+[a-zA-Z]{2,}(/#s)?/(?<KEY>.+)"),
            "https://{any}/#s/{shareKey}",
            "https://kodcloud.com/",
            KdTool.class),
    // 其他自定义域名解析
    OTHER("其他网盘",
       compile("http(s)?://([a-zA-Z\\d]+(-[a-zA-Z\\d]+)*\\.)+[a-zA-Z]{2,}/(?<KEY>.+)"),
               "https://{any}/{shareKey}",
    OtherTool.class);

    public static final String KEY = "KEY";

    public static final String PWD = "PWD";

    // 网盘的显示名称，用于用户界面显示
    private final String displayName;

    // 用于匹配和解析分享链接的正则表达式，保证最后一个捕捉组能匹配到分享key
    private final Pattern pattern;

    private final String regex;

    // 网盘的标准链接模板，不含占位符，用于规范化分享链接
    private final String standardUrlTemplate;

    // 网盘的域名, 如果在分享链接里能提取到, 则可不写
    private String panDomain;

    // 指向解析工具IPanTool实现类
    private final Class<? extends IPanTool> toolClass;

    PanDomainTemplate(String displayName, Pattern pattern, String standardUrlTemplate,
                      Class<? extends IPanTool> toolClass) {
        this.displayName = displayName;
        this.pattern = pattern;
        this.regex = pattern.pattern();
        this.standardUrlTemplate = standardUrlTemplate;
        this.toolClass = toolClass;
    }

    PanDomainTemplate(String displayName, Pattern pattern, String standardUrlTemplate, String panDomain,
                      Class<? extends IPanTool> toolClass) {
        this.displayName = displayName;
        this.pattern = pattern;
        this.regex = pattern.pattern();
        this.standardUrlTemplate = standardUrlTemplate;
        this.panDomain = panDomain;
        this.toolClass = toolClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getRegex() {
        return regex;
    }

    public String getStandardUrlTemplate() {
        return standardUrlTemplate;
    }

    public Class<? extends IPanTool> getToolClass() {
        return toolClass;
    }

    public String getPanDomain() {
        if (panDomain == null) {
            String url = standardUrlTemplate
                    .replace("{shareKey}", "");
            URL panDomainUrl = null;
            try {
                panDomainUrl = new URL(url);
            } catch (MalformedURLException ignored) {}

            return panDomainUrl != null ? (panDomainUrl.getProtocol() + "://" + panDomainUrl.getHost()) : "无";
        }
        return panDomain;
    }
}
