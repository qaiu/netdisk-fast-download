// 修改自 https://github.com/syhyz1990/panAI


    let opt = {
        // 'baidu': {
        //     reg: /((?:https?:\/\/)?(?:e?yun|pan)\.baidu\.com\/(doc\/|enterprise\/)?(?:s\/[\w~]*(((-)?\w*)*)?|share\/\S{4,}))/,
        //     host: /(pan|e?yun)\.baidu\.com/,
        //     input: ['#accessCode', '.share-access-code', '#wpdoc-share-page > .u-dialog__wrapper .u-input__inner'],
        //     button: ['#submitBtn', '.share-access .g-button', '#wpdoc-share-page > .u-dialog__wrapper .u-btn--primary'],
        //     name: '百度网盘',
        //     storage: 'hash'
        // },
        // 'aliyun': {
        //     reg: /((?:https?:\/\/)?(?:(?:www\.)?(?:aliyundrive|alipan)\.com\/s|alywp\.net)\/[a-zA-Z\d]+)/,
        //     host: /www\.(aliyundrive|alipan)\.com|alywp\.net/,
        //     input: ['form .ant-input', 'form input[type="text"]', 'input[name="pwd"]'],
        //     button: ['form .button--fep7l', 'form button[type="submit"]'],
        //     name: '阿里云盘',
        //     storage: 'hash'
        // },
        // 'weiyun': {
        //     reg: /((?:https?:\/\/)?share\.weiyun\.com\/[a-zA-Z\d]+)/,
        //     host: /share\.weiyun\.com/,
        //     input: ['.mod-card-s input[type=password]', 'input.pw-input'],
        //     button: ['.mod-card-s .btn-main', ".pw-btn-wrap button.btn"],
        //     name: '微云',
        //     storage: 'hash'
        // },
        // 'tianyi': {
        //     reg: /((?:https?:\/\/)?cloud\.189\.cn\/(?:t\/|web\/share\?code=)?[a-zA-Z\d]+)/,
        //     host: /cloud\.189\.cn/,
        //     input: ['.access-code-item #code_txt', "input.access-code-input"],
        //     button: ['.access-code-item .visit', ".button"],
        //     name: '天翼云盘',
        //     storage: (() => util.isMobile === true ? 'local' : 'hash')(),
        //     storagePwdName: 'tmp_tianyi_pwd'
        // },
        // 'caiyun': {
        //     reg: /((?:https?:\/\/)?caiyun\.139\.com\/(?:m\/i|w\/i\/|web\/|front\/#\/detail)\??(?:linkID=)?[a-zA-Z\d]+)/,
        //     host: /(?:cai)?yun\.139\.com/,
        //     input: ['.token-form input[type=text]'],
        //     button: ['.token-form .btn-token'],
        //     name: '移动云盘',
        //     storage: 'local',
        //     storagePwdName: 'tmp_caiyun_pwd'
        // },
        // 'xunlei': {
        //     reg: /((?:https?:\/\/)?pan\.xunlei\.com\/s\/[\w-]{10,})/,
        //     host: /pan\.xunlei\.com/,
        //     input: ['.pass-input-wrap .td-input__inner'],
        //     button: ['.pass-input-wrap .td-button'],
        //     name: '迅雷云盘',
        //     storage: 'hash'
        // },
        // '360': {
        //     reg: /((?:https?:\/\/)?(?:[a-zA-Z\d\-.]+)?(?:yunpan\.360\.cn|yunpan\.com)(\/lk)?\/surl_\w{6,})/,
        //     host: /[\w.]+?yunpan\.com/,
        //     input: ['.pwd-input'],
        //     button: ['.submit-btn'],
        //     name: '360云盘',
        //     storage: 'local',
        //     storagePwdName: 'tmp_360_pwd'
        // },
        // '115': {
        //     reg: /((?:https?:\/\/)?115\.com\/s\/[a-zA-Z\d]+)/,
        //     host: /115\.com/,
        //     input: ['.form-decode input'],
        //     button: ['.form-decode .submit a'],
        //     name: '115网盘',
        //     storage: 'hash'
        // },
        // 'quark': {
        //     reg: /((?:https?:\/\/)?pan\.quark\.cn\/s\/[a-zA-Z\d-]+)/,
        //     host: /pan\.quark\.cn/,
        //     input: ['.ant-input'],
        //     button: ['.ant-btn-primary'],
        //     name: '夸克网盘',
        //     storage: 'local',
        //     storagePwdName: 'tmp_quark_pwd'
        // },
        // 'vdisk': {
        //     reg: /(?:https?:\/\/)?vdisk.weibo.com\/lc\/\w+/,
        //     host: /vdisk\.weibo\.com/,
        //     input: ['#keypass', "#access_code"],
        //     button: ['.search_btn_wrap a', "#linkcommon_btn"],
        //     name: '微盘',
        //     storage: 'hash',
        // },
        // 'uc': {
        //     reg: /(?:https?:\/\/)?drive\.uc\.cn\/s\/[a-zA-Z\d]+/,
        //     host: /drive\.uc\.cn/,
        //     input: ["input[class*='ShareReceivePC--input']", '.input-wrap input'],
        //     button: ["button[class*='ShareReceivePC--submit-btn'", '.input-wrap button'],
        //     name: 'UC云盘',
        //     storage: 'hash'
        // },
        // 'jianguoyun': {
        //     reg: /((?:https?:\/\/)?www\.jianguoyun\.com\/p\/[\w-]+)/,
        //     host: /www\.jianguoyun\.com/,
        //     input: ['input[type=password]'],
        //     button: ['.ok-button', '.confirm-button'],
        //     name: '坚果云',
        //     storage: 'hash'
        // },
        // 'wo': {
        //     reg: /(?:https?:\/\/)?pan\.wo\.cn\/s\/[\w_]+/,
        //     host: /(pan\.wo\.cn|panservice\.mail\.wo\.cn)/,
        //     input: ['input.el-input__inner', ".van-field__control"],
        //     button: ['.s-button', ".share-code button"],
        //     name: '联通云盘',
        //     storage: (() => util.isMobile === true ? 'local' : 'hash')(),
        //     storagePwdName: 'tmp_wo_pwd'
        // },
        // 'mega': {
        //     reg: /((?:https?:\/\/)?(?:mega\.nz|mega\.co\.nz)\/#F?![\w!-]+)/,
        //     host: /(?:mega\.nz|mega\.co\.nz)/,
        //     input: ['.dlkey-dialog input'],
        //     button: ['.dlkey-dialog .fm-dialog-new-folder-button'],
        //     name: 'Mega',
        //     storage: 'local'
        // },
        // '520vip': {
        //     reg: /((?:https?:\/\/)?www\.(?:520-vip|eos-53)\.com\/file-\d+\.html)/,
        //     host: /www\.520-vip\.com/,
        //     name: '520云盘',
        // },
        // '567pan': {
        //     reg: /((?:https?:\/\/)?www\.567(?:pan|yun|file|inc)\.(?:com|cn)\/file-\d+\.html)/,
        //     host: /www\.567inc\.cn/,
        //     name: '567盘',
        //     replaceHost: "www.567inc.com",
        // },
        // 'ayunpan': {
        //     reg: /((?:https?:\/\/)?www\.ayunpan\.com\/file-\d+\.html)/,
        //     host: /www\.ayunpan\.com/,
        //     name: 'AYunPan',
        // },
        // 'iycdn.com': {
        //     reg: /((?:https?:\/\/)?www\.iycdn\.com\/file-\d+\.html)/,
        //     host: /www\.iycdn\.com/,
        //     name: '爱优网盘',
        // },
        // 'feimaoyun': {
        //     reg: /((?:https?:\/\/)?www\.feimaoyun\.com\/s\/[0-9a-zA-Z]+)/,
        //     host: /www\.feimaoyun\.com/,
        //     name: '飞猫盘',
        // },
        // 'uyunp.com': {
        //     reg: /((?:https?:\/\/)?download\.uyunp\.com\/share\/s\/short\/\?surl=[0-9a-zA-Z]+)/,
        //     host: /download\.uyunp\.com/,
        //     name: '优云下载',
        // },
        // 'dudujb': {
        //     reg: /(?:https?:\/\/)?www\.dudujb\.com\/file-\d+\.html/,
        //     host: /www\.dudujb\.com/,
        //     name: '贵族网盘',
        // },
        // 'xunniu': {
        //     reg: /(?:https?:\/\/)?www\.xunniu(?:fxp|wp|fx)\.com\/file-\d+\.html/,
        //     host: /www\.xunniuwp\.com/,
        //     name: '迅牛网盘',
        // },
        // 'xueqiupan': {
        //     reg: /(?:https?:\/\/)?www\.xueqiupan\.com\/file-\d+\.html/,
        //     host: /www\.xueqiupan\.com/,
        //     name: '雪球云盘',
        // },
        // '77file': {
        //     reg: /(?:https?:\/\/)?www\.77file\.com\/s\/[a-zA-Z\d]+/,
        //     host: /www\.77file\.com/,
        //     name: '77file',
        // },
        // 'ownfile': {
        //     reg: /(?:https?:\/\/)?ownfile\.net\/files\/[a-zA-Z\d]+\.html/,
        //     host: /ownfile\.net/,
        //     name: 'OwnFile',
        // },
        // 'feiyunfile': {
        //     reg: /(?:https?:\/\/)?www\.feiyunfile\.com\/file\/[\w=]+\.html/,
        //     host: /www\.feiyunfile\.com/,
        //     name: '飞云网盘',
        // },
        // 'yifile': {
        //     reg: /(?:https?:\/\/)?www\.yifile\.com\/f\/\w+/,
        //     host: /www\.yifile\.com/,
        //     name: 'YiFile',
        // },
        // 'dufile': {
        //     reg: /(?:https?:\/\/)?dufile\.com\/file\/\w+\.html/,
        //     host: /dufile\.com/,
        //     name: 'duFile',
        // },
        // 'flowus': {
        //     reg: /((?:https?:\/\/)?flowus\.cn\/[\S ^\/]*\/?share\/[a-z\d]{8}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{12})/,
        //     host: /flowus\.cn/,
        //     name: 'FlowUs息流',
        //     storage: 'hash'
        // },
        // 'chrome': {
        //     reg: /^https?:\/\/chrome.google.com\/webstore\/.+?\/([a-z]{32})(?=[\/#?]|$)/,
        //     host: /chrome\.google\.com/,
        //     replaceHost: "chrome.crxsoso.com",
        //     name: 'Chrome商店',
        // },
        // 'edge': {
        //     reg: /^https?:\/\/microsoftedge.microsoft.com\/addons\/.+?\/([a-z]{32})(?=[\/#?]|$)/,
        //     host: /microsoftedge\.microsoft\.com/,
        //     replaceHost: "microsoftedge.crxsoso.com",
        //     name: 'Edge商店',
        // },
        // 'firefox': {
        //     reg: /^https?:\/\/(reviewers\.)?(addons\.mozilla\.org|addons(?:-dev)?\.allizom\.org)\/.*?(?:addon|review)\/([^/<>"'?#]+)/,
        //     host: /addons\.mozilla\.org/,
        //     replaceHost: "addons.crxsoso.com",
        //     name: 'Firefox商店',
        // },
        // 'microsoft': {
        //     reg: /^https?:\/\/(?:apps|www).microsoft.com\/(?:store|p)\/.+?\/([a-zA-Z\d]{10,})(?=[\/#?]|$)/,
        //     host: /(apps|www)\.microsoft\.com/,
        //     replaceHost: "apps.crxsoso.com",
        //     name: 'Windows商店',
        // }

        'lanzou': {
            reg: /((?:https?:\/\/)?(?:[a-zA-Z0-9\-.]+)?(?:lanzou[a-z]|lanzn)\.com\/[a-zA-Z\d_\-]+(?:\/[\w-]+)?)/,
            host: /(?:[a-zA-Z\d-.]+)?(?:lanzou[a-z]|lanzn)\.com/,
            input: ['#pwd'],
            button: ['.passwddiv-btn', '#sub'],
            name: '蓝奏云',
            storage: 'hash'
        },
        'cowtransfer': {
            reg: /((?:https?:\/\/)?(?:[a-zA-Z\d-.]+)?cowtransfer\.com\/s\/[a-zA-Z\d]+)/,
            host: /(?:[a-zA-Z\d-.]+)?cowtransfer\.com/,
            input: ['.receive-code-input input'],
            button: ['.open-button'],
            name: '奶牛快传',
            storage: 'hash'
        },
        'ctfile': {
            reg: /((?:https?:\/\/)?(?:[a-zA-Z\d-.]+)?(?:ctfile|545c|u062|ghpym|474b)\.com\/\w+\/[a-zA-Z\d-]+)/,
            host: /(?:[a-zA-Z\d-.]+)?(?:ctfile|545c|u062|474b)\.com/,
            input: ['#passcode'],
            button: ['.card-body button'],
            name: '城通网盘',
            storage: 'hash'
        },
        '123pan': {
            reg: /((?:https?:\/\/)?www\.(123pan|123865|123684)\.com\/s\/[\w-]{6,})/,
            host: /www\.123pan\.com/,
            input: ['.ca-fot input', ".appinput .appinput"],
            button: ['.ca-fot button', ".appinput button"],
            name: '123云盘',
            storage: 'hash'
        },
        'wenshushu': {
            reg: /((?:https?:\/\/)?(?:www\.wenshushu|ws28)\.cn\/(?:k|box|f)\/\w+)/,
            host: /www\.wenshushu\.cn/,
            input: ['.pwd-inp .ivu-input'],
            button: ['.pwd-inp .ivu-btn'],
            name: '文叔叔网盘',
            storage: 'hash'
        },

        // ---new---
        feijix: {
            reg: /https:\/\/(share\.feijipan\.com|www\.feijix\.com)\/s\/.+/,
            host: /feijix\.com|feijipan\.com/,
            name: '小飞机网盘'
        },
        lecloud: {
            reg: /https:\/\/lecloud\.lenovo\.com\/share\/.+/,
            host: /lenovo\.com/,
            name: '联想乐云'
        },
        fangcloud: {
            reg: /https:\/\/v2\.fangcloud\.(com|cn)\/(s|sharing)\/.+/,
            host: /fangcloud\.(com|cn)/,
            name: '亿方云'
        },
        ilanzou: {
            reg: /https:\/\/www\.ilanzou\.com\/s\/.+/,
            host: /ilanzou\.com/,
            name: '蓝奏云优享'
        },
        qqMailTransfer: {
            reg: /https:\/\/iwx\.mail\.qq\.com\/ftn\/download\?.+/,
            host: /mail\.qq\.com/,
            name: 'QQ邮箱中转站'
        },
        QQsc: {
            // qfile.qq.com
            reg: /https:\/\/qfile\.qq\.com\/q\/.+/,
            host: /qfile\.qq\.com/,
            name: 'QQ闪传'
        },
        pan118: {
            reg: /https:\/\/(?:[a-zA-Z\d-]+\.)?118pan\.com\/b.+/,
            host: /118pan\.com/,
            name: '118网盘'
        },
        pan115: {
            // https://115.com/s/swhyiia3wzi?password=h374
            reg: /https:\/\/(115|anxia)\.com\/s\/.+/,
            host: /115pan\.com/,
            name: '115网盘'
        },
        onedrive: {
            reg: /https:\/\/1drv\.ms\/.+/,
            host: /1drv\.ms/,
            name: 'OneDrive'
        },
        googleDrive: {
            reg: /https:\/\/drive\.google\.com\/file\/d\/.+\/view\?usp=(sharing|drive_link)?/,
            host: /drive\.google\.com/,
            name: 'GoogleDrive'
        },
        icloud: {
            reg: /https:\/\/www\.icloud\.com\.cn\/iclouddrive\/([a-zA-Z\d_-]+)(#.+)?/,
            host: /www\.icloud\.com\.cn/,
            name: 'iCloud',
        },
        n163Music: {
            reg: /https:\/\/163cn\.tv\/.+/,
            host: /163cn\.tv/,
            name: '网易云音乐分享'
        },
        qqMusic: {
            reg: /https:\/\/y\.qq\.com\/n\/ryqq\/songDetail\/.+/,
            host: /y\.qq\.com/,
            name: 'QQ音乐歌曲详情'
        },
        kuwoMusic: {
            reg: /https:\/\/(m\.)?kuwo\.cn\/(newh5app\/)?play_detail\/.+/,
            host: /kuwo\.cn/,
            name: '酷我音乐分享'
        },
        miguMusic: {
            reg: /https:\/\/music\.migu\.cn\/v3\/music\/song\/.+/,
            host: /migu\.cn/,
            name: '咪咕音乐分享'
        },
        other: {
            reg: /https:\/\/([a-zA-Z0-9]+(-[a-zA-Z0-9]+)*\.)+[a-zA-Z]{2,}\/s\/.+/,
            host: /.*/,
            name: '其他/Cloudreve/可道云'
        },

    };

    let main = {
        //正则解析网盘链接
        parseLink(text = '') {
            let obj = {name: '', link: '', storage: '', storagePwdName: ''};
            if (text) {
                try {
                    text = decodeURIComponent(text);
                } catch {
                }
                text = text.replace(/[点點]/g, '.');
                text = text.replace(/[\u4e00-\u9fa5()（）,\u200B，\uD83C-\uDBFF\uDC00-\uDFFF]/g, '');
                text = text.replace(/lanzous/g, 'lanzouw'); //修正lanzous打不开的问题
                for (let name in opt) {
                    let val = opt[name];
                    if (val.reg.test(text)) {
                        let matches = text.match(val.reg);
                        obj.name = val.name;
                        obj.link = matches[0];
                        obj.storage = val.storage;
                        obj.storagePwdName = val.storagePwdName || null;
                        if (val.replaceHost) {
                            obj.link = obj.link.replace(val.host, val.replaceHost);
                        }
                        return obj;
                    }
                }
            }
            return obj;
        },

        //正则解析提取码
        parsePwd(text = '') {
            text = text.replace(/\u200B/g, '').replace('%3A', ":");
            text = text.replace(/(?:本帖)?隐藏的?内容[：:]?/, "");
            let reg = /wss:[a-zA-Z0-9]+|(?<=\s*(?:密|提取|访问|訪問|key|password|pwd|#|\?p=)\s*[码碼]?\s*[：:=]?\s*)[a-zA-Z0-9]{3,8}/i;
            if (reg.test(text)) {
                let match = text.match(reg);
                return match[0];
            }
            return '';
        },
    };


export default {
    parseLink: main.parseLink,
    parsePwd: main.parsePwd,
    opt: opt
}
