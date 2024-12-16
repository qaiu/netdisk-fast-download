package cn.qaiu.parser;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.impl.PodTool;

public class ParserUrlOut {

    //https://onedrive.live.com/redir?resid=ABFD0A26E47D3458!4699&e=OggA4s&migratedtospo=true&redeem=aHR0cHM6Ly8xZHJ2Lm1zL3UvcyFBbGcwZmVRbUN2MnJwRnZ1NDQ0aGc1eVZxRGNLP2U9T2dnQTRz
    public static void main(String[] args) {
//        Matcher matcher = redirectUrlRegex.matcher("https://onedrive.live.com/redir?resid=ABFD0A26E47D3458!4698" +
//                "&authkey=!ACpvXghP5xhG_cg&e=hV98W1");
//        if (matcher.find()) {
//            System.out.println(matcher.group("cid"));
//            System.out.println(matcher.group("cid2"));
//            System.out.println(matcher.group("authkey"));
//        }
        // appid 5cbed6ac-a083-4e14-b191-b4ba07653de2 5cbed6ac-a083-4e14-b191-b4ba07653de2
        // https://my.microsoftpersonalcontent.com/personal/abfd0a26e47d3458/_layouts/15/embed.aspx?UniqueId=e47d3458-0a26-20fd-80ab-5b1200000000&Translate=false&ApiVersion=2.0
        // https://my.microsoftpersonalcontent.com/personal/abfd0a26e47d3458/_layouts/15/embed.aspx?UniqueId=6b0900d6-abcf-44ce-b7bf-7b626bcbe4b8&Translate=false&ApiVersion=2.0
        // https://1drv.ms/u/s!Alg0feQmCv2rpFvu444hg5yVqDcK?e=OggA4s
        // https://1drv.ms/u/c/abfd0a26e47d3458/EVg0feQmCv0ggKtbEgAAAAABqGv8K6HmOwLRsvokyV5fUg?e=iqoRc0
        // https://1drv.ms/u/c/abfd0a26e47d3458/EVg0feQmCv0ggKtaEgAAAAAB-lF1qjkfv5OqdrT9VSMDMw
        new PodTool(ShareLinkInfo.newBuilder().shareUrl("https://1drv.ms/u/c/abfd0a26e47d3458/EdYACWvPq85Et797YmvL5LgBruUKoNxqIFATXhIv1PI2_Q")
                .build())
                .parse().onSuccess(System.out::println);

    }
}
