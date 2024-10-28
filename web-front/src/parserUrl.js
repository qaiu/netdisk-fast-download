// ==UserScript==
// @name              网盘智能识别助手
// @namespace         https://github.com/syhyz1990/panAI
// @version           2.0.2
// @author            YouXiaoHou,52fisher
// @description       智能识别选中文字中的🔗网盘链接和🔑提取码，识别成功打开网盘链接并自动填写提取码，省去手动复制提取码在输入的烦恼。支持识别 ✅百度网盘 ✅阿里云盘 ✅腾讯微云 ✅蓝奏云 ✅天翼云盘 ✅移动云盘 ✅迅雷云盘 ✅123云盘 ✅360云盘 ✅115网盘 ✅奶牛快传 ✅城通网盘 ✅夸克网盘 ✅FlowUs息流 ✅Chrome 扩展商店 ✅Edge 扩展商店 ✅Firefox 扩展商店 ✅Windows 应用商店。
// @license           AGPL-3.0-or-later
// @homepage          https://www.youxiaohou.com/tool/install-panai.html
// @supportURL        https://github.com/syhyz1990/panAI
// @updateURL         https://www.youxiaohou.com/panai.user.js
// @downloadURL       https://www.youxiaohou.com/panai.user.js
// @match             *://*/*
// @require           https://unpkg.com/sweetalert2@10.16.6/dist/sweetalert2.min.js
// @require           https://unpkg.com/hotkeys-js@3.13.3/dist/hotkeys.min.js
// @resource          swalStyle https://unpkg.com/sweetalert2@10.16.6/dist/sweetalert2.min.css
// @run-at            document-idle
// @grant             GM_openInTab
// @grant             GM_setValue
// @grant             GM_getValue
// @grant             GM_registerMenuCommand
// @grant             GM_getResourceText
// @grant             GM_info
// @icon              data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMjggMTI4Ij48cGF0aCBkPSJNMTAzLjYgMTA3LjRjMy41LTIuMiA4LjktNi4xIDEzLjgtMTIuNXM3LjMtMTIuNSA4LjUtMTYuNWMuNS0xLjcgMi4yLTcuNSAyLjItMTQuNyAwLTEwLjEtMy4zLTI1LjEtMTUuNC0zNi44LTE0LjUtMTQtMzIuMS0xNC4zLTM1LjctMTQuMy04IDAtMTUuNyAxLjktMjIuNiA1LjJDNDQgMjMgMzUuNyAzMS40IDMwLjggNDEuN2MtMS4zIDIuOC00IDQuNy03LjEgNS00IC4zLTcuNSA0LjQtOC45IDkuNi0uNSAxLjktMS42IDMuNS0zLjEgNC43QzQuNCA2Ni44IDAgNzUuNyAwIDg1YzAgNi44IDIuMyAxMy4xIDYuMSAxOC4yIDUuNSA3LjQgMTQuMiAxMi4yIDI0IDEyLjJoNDcuMWM0LjQgMCAxMS0uNSAxOC4zLTMuNSAzLjItMS40IDUuOS0zIDguMS00LjV6IiBmaWxsPSIjNDQ0Ii8+PHBhdGggZD0iTTExOS44IDY0LjNjLjEtMTcuMS0xMC40LTI4LTEyLjUtMzAuMUM5NSAyMi4xIDc5LjkgMjEuOCA3Ni45IDIxLjhjLTE3LjYgMC0zMy4zIDEwLjUtMzkuOSAyNi43LS42IDEuMy0xLjggMi4zLTMuNCAyLjNoLS40Yy01LjggMC0xMC42IDQuOC0xMC42IDEwLjd2LjVjMCAxLjQtLjggMi42LTEuOSAzLjNDMTMuNCA2OSA4LjggNzYuOCA4LjggODVjMCAxMi4yIDkuOSAyMi4zIDIyLjIgMjIuM2g0NS4yYzMuNi0uMSAxNy42LS45IDI5LjYtMTIgMi45LTIuOCAxMy45LTEzLjcgMTQtMzF6IiBmaWxsPSIjZGI4NDEyIi8+PHBhdGggZD0iTTExMC44IDU3LjRsLjIgMy4zYzAgMS4zLTEuMSAyLjQtMi4zIDIuNC0xLjMgMC0yLjMtMS4xLTIuMy0yLjRsLS4xLTIuOHYtLjNjMC0xLjIuOS0yLjIgMi4xLTIuM2guM2MuNyAwIDEuMy4zIDEuNy43LS4yLjEuMy41LjQgMS40em0tMy4zLTEwLjNjMCAxLjItMSAyLjMtMi4yIDIuM2gtLjFjLS44IDAtMS42LS41LTItMS4yLTQuNi04LjMtMTMuMy0xMy41LTIyLjgtMTMuNS0xLjIgMC0yLjMtMS0yLjMtMi4ydi0uMWMwLTEuMiAxLTIuMyAyLjItMi4zaC4xYTMwLjM3IDMwLjM3IDAgMCAxIDE1LjggNC40YzQuNiAyLjggOC40IDYuOCAxMS4xIDExLjUuMS4zLjIuNy4yIDEuMXpNNjkuMiA0OWwxOS40IDE0LjhjMS45IDEuNSAzLjEgMy41IDMuNSA1Ljd2LjJjLjEuNC4xLjguMSAxLjIgMCAuNi0uMSAxLjEtLjIgMS42LS40IDIuMi0xLjcgNC4yLTMuNSA1LjZMNjkuMyA5M2MtMi42IDItNS40IDIuNS03LjcgMS40LS4xLS4xLS4yLS4xLS4yLS4yLTItMS4yLTMuMi0zLjUtMy4yLTYuNHYtNi42aC01LjdjLTYuOCAwLTEyLTQuNy0xMi0xMC45IDAtNC44IDIuNi04LjUgNy4yLTEwLjMgMS4zLS41IDIuNy4yIDMuMiAxLjVzLS4xIDIuOC0xLjQgMy4zYy0yLjcgMS4xLTQgMi45LTQgNS41IDAgMy41IDMgNiA3IDZoOC4xYy41IDAgMSAuMiAxLjQuNi43LjYgMS4xIDEuNyAxLjEgMi42djguNGMwIDEuMy40IDIgLjcgMi4xLjQuMiAxLjMgMCAyLjQtLjlsMTkuMi0xNC45YzEuMi0uOSAxLjgtMi4xIDEuOC0zLjNzLS42LTIuMy0xLjctMy4xTDY2LjIgNTNjLTEuMS0uOS0yLTEuMS0yLjQtLjktLjMuMi0uNy45LS43IDIuMXY3LjZjMCAuOS0uNSAxLjctMS4yIDIuMS0uNC4zLS44LjQtMS4zLjQtMS40IDAtMi41LTEuMS0yLjUtMi41di03LjZjMC0zLjEgMS4zLTUuNSAzLjUtNi42bC43LS4zYzIuMS0uNyA0LjYtLjEgNi45IDEuN3oiIGZpbGw9IiM0NDQiLz48L3N2Zz4=
// ==/UserScript==

(function () {
    'use strict';

    const customClass = {
        container: 'panai-container',
        popup: 'panai-popup',
    };

    let toast = Swal.mixin({
        toast: true,
        position: 'top',
        showConfirmButton: false,
        timer: 3500,
        timerProgressBar: false,
        didOpen: (toast) => {
            toast.addEventListener('mouseenter', Swal.stopTimer);
            toast.addEventListener('mouseleave', Swal.resumeTimer);
        }
    });

    let util = {
        clog(c) {
            console.group("%c %c [网盘智能识别助手]", `background:url(${GM_info.script.icon}) center center no-repeat;background-size:12px;padding:3px`, "");
            console.log(c);
            console.groupEnd();
        },

        parseQuery(name) {
            let reg = new RegExp(`(?<=(?:${name})\\=)(?:wss:[a-zA-Z0-9]+|[\\w-]+)`, "i")
            let pd = location.href.replace(/%3A/g, ":").match(reg);
            if (pd) {
                return pd[0];
            }
            return null;
        },

        getValue(name) {
            return GM_getValue(name);
        },

        setValue(name, value) {
            GM_setValue(name, value);
        },

        sleep(time) {
            return new Promise((resolve) => setTimeout(resolve, time));
        },

        addStyle(id, tag, css) {
            tag = tag || 'style';
            let doc = document, styleDom = doc.getElementById(id);
            if (styleDom) return;
            let style = doc.createElement(tag);
            style.rel = 'stylesheet';
            style.id = id;
            tag === 'style' ? style.innerHTML = css : style.href = css;
            document.head.appendChild(style);
        },

        isHidden(el) {
            try {
                return el.offsetParent === null;
            } catch (e) {
                return false;
            }
        },

        isMobile: (() => !!navigator.userAgent.match(/(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone|HarmonyOS|MicroMessenger)/i))(),

        query(selector) {
            if (Array.isArray(selector)) {
                let obj = null;
                for (let i = 0; i < selector.length; i++) {
                    let o = document.querySelector(selector[i]);
                    if (o) {
                        obj = o;
                        break;
                    }
                }
                return obj;
            }
            return document.querySelector(selector);
        }
    };

    let opt = {
        'baidu': {
            reg: /((?:https?:\/\/)?(?:e?yun|pan)\.baidu\.com\/(doc\/|enterprise\/)?(?:s\/[\w~]*(((-)?\w*)*)?|share\/\S{4,}))/,
            host: /(pan|e?yun)\.baidu\.com/,
            input: ['#accessCode', '.share-access-code', '#wpdoc-share-page > .u-dialog__wrapper .u-input__inner'],
            button: ['#submitBtn', '.share-access .g-button', '#wpdoc-share-page > .u-dialog__wrapper .u-btn--primary'],
            name: '百度网盘',
            storage: 'hash'
        },
        'aliyun': {
            reg: /((?:https?:\/\/)?(?:(?:www\.)?(?:aliyundrive|alipan)\.com\/s|alywp\.net)\/[a-zA-Z\d]+)/,
            host: /www\.(aliyundrive|alipan)\.com|alywp\.net/,
            input: ['form .ant-input', 'form input[type="text"]', 'input[name="pwd"]'],
            button: ['form .button--fep7l', 'form button[type="submit"]'],
            name: '阿里云盘',
            storage: 'hash'
        },
        'weiyun': {
            reg: /((?:https?:\/\/)?share\.weiyun\.com\/[a-zA-Z\d]+)/,
            host: /share\.weiyun\.com/,
            input: ['.mod-card-s input[type=password]', 'input.pw-input'],
            button: ['.mod-card-s .btn-main', ".pw-btn-wrap button.btn"],
            name: '微云',
            storage: 'hash'
        },
        'lanzou': {
            reg: /((?:https?:\/\/)?(?:[a-zA-Z0-9\-.]+)?(?:lanzou[a-z]|lanzn)\.com\/[a-zA-Z\d_\-]+(?:\/[\w-]+)?)/,
            host: /(?:[a-zA-Z\d-.]+)?(?:lanzou[a-z]|lanzn)\.com/,
            input: ['#pwd'],
            button: ['.passwddiv-btn', '#sub'],
            name: '蓝奏云',
            storage: 'hash'
        },
        'tianyi': {
            reg: /((?:https?:\/\/)?cloud\.189\.cn\/(?:t\/|web\/share\?code=)?[a-zA-Z\d]+)/,
            host: /cloud\.189\.cn/,
            input: ['.access-code-item #code_txt', "input.access-code-input"],
            button: ['.access-code-item .visit', ".button"],
            name: '天翼云盘',
            storage: (() => util.isMobile === true ? 'local' : 'hash')(),
            storagePwdName: 'tmp_tianyi_pwd'
        },
        'caiyun': {
            reg: /((?:https?:\/\/)?caiyun\.139\.com\/(?:m\/i|w\/i\/|web\/|front\/#\/detail)\??(?:linkID=)?[a-zA-Z\d]+)/,
            host: /(?:cai)?yun\.139\.com/,
            input: ['.token-form input[type=text]'],
            button: ['.token-form .btn-token'],
            name: '移动云盘',
            storage: 'local',
            storagePwdName: 'tmp_caiyun_pwd'
        },
        'xunlei': {
            reg: /((?:https?:\/\/)?pan\.xunlei\.com\/s\/[\w-]{10,})/,
            host: /pan\.xunlei\.com/,
            input: ['.pass-input-wrap .td-input__inner'],
            button: ['.pass-input-wrap .td-button'],
            name: '迅雷云盘',
            storage: 'hash'
        },
        '123pan': {
            reg: /((?:https?:\/\/)?www\.123pan\.com\/s\/[\w-]{6,})/,
            host: /www\.123pan\.com/,
            input: ['.ca-fot input', ".appinput .appinput"],
            button: ['.ca-fot button', ".appinput button"],
            name: '123云盘',
            storage: 'hash'
        },
        '360': {
            reg: /((?:https?:\/\/)?(?:[a-zA-Z\d\-.]+)?(?:yunpan\.360\.cn|yunpan\.com)(\/lk)?\/surl_\w{6,})/,
            host: /[\w.]+?yunpan\.com/,
            input: ['.pwd-input'],
            button: ['.submit-btn'],
            name: '360云盘',
            storage: 'local',
            storagePwdName: 'tmp_360_pwd'
        },
        '115': {
            reg: /((?:https?:\/\/)?115\.com\/s\/[a-zA-Z\d]+)/,
            host: /115\.com/,
            input: ['.form-decode input'],
            button: ['.form-decode .submit a'],
            name: '115网盘',
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
            reg: /((?:https?:\/\/)?(?:[a-zA-Z\d-.]+)?(?:ctfile|545c|u062|ghpym)\.com\/\w+\/[a-zA-Z\d-]+)/,
            host: /(?:[a-zA-Z\d-.]+)?(?:ctfile|545c|u062)\.com/,
            input: ['#passcode'],
            button: ['.card-body button'],
            name: '城通网盘',
            storage: 'hash'
        },
        'quark': {
            reg: /((?:https?:\/\/)?pan\.quark\.cn\/s\/[a-zA-Z\d-]+)/,
            host: /pan\.quark\.cn/,
            input: ['.ant-input'],
            button: ['.ant-btn-primary'],
            name: '夸克网盘',
            storage: 'local',
            storagePwdName: 'tmp_quark_pwd'
        },
        'vdisk': {
            reg: /(?:https?:\/\/)?vdisk.weibo.com\/lc\/\w+/,
            host: /vdisk\.weibo\.com/,
            input: ['#keypass', "#access_code"],
            button: ['.search_btn_wrap a', "#linkcommon_btn"],
            name: '微盘',
            storage: 'hash',
        },
        'wenshushu': {
            reg: /((?:https?:\/\/)?(?:www\.wenshushu|ws28)\.cn\/(?:k|box|f)\/\w+)/,
            host: /www\.wenshushu\.cn/,
            input: ['.pwd-inp .ivu-input'],
            button: ['.pwd-inp .ivu-btn'],
            name: '文叔叔网盘',
            storage: 'hash'
        },
        'uc': {
            reg: /(?:https?:\/\/)?drive\.uc\.cn\/s\/[a-zA-Z\d]+/,
            host: /drive\.uc\.cn/,
            input: ["input[class*='ShareReceivePC--input']", '.input-wrap input'],
            button: ["button[class*='ShareReceivePC--submit-btn'", '.input-wrap button'],
            name: 'UC云盘',
            storage: 'hash'
        },
        'jianguoyun': {
            reg: /((?:https?:\/\/)?www\.jianguoyun\.com\/p\/[\w-]+)/,
            host: /www\.jianguoyun\.com/,
            input: ['input[type=password]'],
            button: ['.ok-button', '.confirm-button'],
            name: '坚果云',
            storage: 'hash'
        },
        'wo': {
            reg: /(?:https?:\/\/)?pan\.wo\.cn\/s\/[\w_]+/,
            host: /(pan\.wo\.cn|panservice\.mail\.wo\.cn)/,
            input: ['input.el-input__inner', ".van-field__control"],
            button: ['.s-button', ".share-code button"],
            name: '联通云盘',
            storage: (() => util.isMobile === true ? 'local' : 'hash')(),
            storagePwdName: 'tmp_wo_pwd'
        },
        'mega': {
            reg: /((?:https?:\/\/)?(?:mega\.nz|mega\.co\.nz)\/#F?![\w!-]+)/,
            host: /(?:mega\.nz|mega\.co\.nz)/,
            input: ['.dlkey-dialog input'],
            button: ['.dlkey-dialog .fm-dialog-new-folder-button'],
            name: 'Mega',
            storage: 'local'
        },
        '520vip': {
            reg: /((?:https?:\/\/)?www\.(?:520-vip|eos-53)\.com\/file-\d+\.html)/,
            host: /www\.520-vip\.com/,
            name: '520云盘',
        },
        '567pan': {
            reg: /((?:https?:\/\/)?www\.567(?:pan|yun|file|inc)\.(?:com|cn)\/file-\d+\.html)/,
            host: /www\.567inc\.cn/,
            name: '567盘',
            replaceHost: "www.567inc.com",
        },
        'ayunpan': {
            reg: /((?:https?:\/\/)?www\.ayunpan\.com\/file-\d+\.html)/,
            host: /www\.ayunpan\.com/,
            name: 'AYunPan',
        },
        'iycdn.com': {
            reg: /((?:https?:\/\/)?www\.iycdn\.com\/file-\d+\.html)/,
            host: /www\.iycdn\.com/,
            name: '爱优网盘',
        },
        'feimaoyun': {
            reg: /((?:https?:\/\/)?www\.feimaoyun\.com\/s\/[0-9a-zA-Z]+)/,
            host: /www\.feimaoyun\.com/,
            name: '飞猫盘',
        },
        'uyunp.com': {
            reg: /((?:https?:\/\/)?download\.uyunp\.com\/share\/s\/short\/\?surl=[0-9a-zA-Z]+)/,
            host: /download\.uyunp\.com/,
            name: '优云下载',
        },
        'dudujb': {
            reg: /(?:https?:\/\/)?www\.dudujb\.com\/file-\d+\.html/,
            host: /www\.dudujb\.com/,
            name: '贵族网盘',
        },
        'xunniu': {
            reg: /(?:https?:\/\/)?www\.xunniu(?:fxp|wp|fx)\.com\/file-\d+\.html/,
            host: /www\.xunniuwp\.com/,
            name: '迅牛网盘',
        },
        'xueqiupan': {
            reg: /(?:https?:\/\/)?www\.xueqiupan\.com\/file-\d+\.html/,
            host: /www\.xueqiupan\.com/,
            name: '雪球云盘',
        },
        '77file': {
            reg: /(?:https?:\/\/)?www\.77file\.com\/s\/[a-zA-Z\d]+/,
            host: /www\.77file\.com/,
            name: '77file',
        },
        'ownfile': {
            reg: /(?:https?:\/\/)?ownfile\.net\/files\/[a-zA-Z\d]+\.html/,
            host: /ownfile\.net/,
            name: 'OwnFile',
        },
        'feiyunfile': {
            reg: /(?:https?:\/\/)?www\.feiyunfile\.com\/file\/[\w=]+\.html/,
            host: /www\.feiyunfile\.com/,
            name: '飞云网盘',
        },
        'yifile': {
            reg: /(?:https?:\/\/)?www\.yifile\.com\/f\/\w+/,
            host: /www\.yifile\.com/,
            name: 'YiFile',
        },
        'dufile': {
            reg: /(?:https?:\/\/)?dufile\.com\/file\/\w+\.html/,
            host: /dufile\.com/,
            name: 'duFile',
        },
        'flowus': {
            reg: /((?:https?:\/\/)?flowus\.cn\/[\S ^\/]*\/?share\/[a-z\d]{8}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{4}-[a-z\d]{12})/,
            host: /flowus\.cn/,
            name: 'FlowUs息流',
            storage: 'hash'
        },
        'chrome': {
            reg: /^https?:\/\/chrome.google.com\/webstore\/.+?\/([a-z]{32})(?=[\/#?]|$)/,
            host: /chrome\.google\.com/,
            replaceHost: "chrome.crxsoso.com",
            name: 'Chrome商店',
        },
        'edge': {
            reg: /^https?:\/\/microsoftedge.microsoft.com\/addons\/.+?\/([a-z]{32})(?=[\/#?]|$)/,
            host: /microsoftedge\.microsoft\.com/,
            replaceHost: "microsoftedge.crxsoso.com",
            name: 'Edge商店',
        },
        'firefox': {
            reg: /^https?:\/\/(reviewers\.)?(addons\.mozilla\.org|addons(?:-dev)?\.allizom\.org)\/.*?(?:addon|review)\/([^/<>"'?#]+)/,
            host: /addons\.mozilla\.org/,
            replaceHost: "addons.crxsoso.com",
            name: 'Firefox商店',
        },
        'microsoft': {
            reg: /^https?:\/\/(?:apps|www).microsoft.com\/(?:store|p)\/.+?\/([a-zA-Z\d]{10,})(?=[\/#?]|$)/,
            host: /(apps|www)\.microsoft\.com/,
            replaceHost: "apps.crxsoso.com",
            name: 'Windows商店',
        }
    };

    let main = {
        lastText: "lorem&",

        //初始化配置数据
        initValue() {
            let value = [{
                name: 'setting_success_times',
                value: 0
            }, {
                name: 'setting_auto_click_btn',
                value: true
            }, {
                name: 'setting_active_in_front',
                value: true
            }, {
                name: 'setting_timer_open',
                value: false
            }, {
                name: 'setting_timer',
                value: 5000
            }, {
                name: 'setting_hotkeys',
                value: 'F1'
            }];

            value.forEach((v) => {
                if (util.getValue(v.name) === undefined) {
                    util.setValue(v.name, v.value);
                }
            });
        },

        // 监听选择事件
        addPageListener() {
            document.addEventListener("mouseup", this.smartIdentify.bind(this), true);
            document.addEventListener("keydown", this.pressKey.bind(this), true);
        },

        // ⚠️可能会增加时间⚠️ 如果有需要可以增加选项
        // 获取选择内容的HTML和文本(增加兼容性) 或 DOM（节点遍历）
        getSelectionHTML(selection, isDOM = false) {
            const testDiv = document.createElement("div");
            if (!selection.isCollapsed) {
                // Range 转 DocumentFragment
                const docFragment = selection.getRangeAt(0).cloneContents();
                testDiv.appendChild(docFragment);
            }
            // 拼接选中文本，增加兼容
            return isDOM ? testDiv : selection.toString();
        },

        smartIdentify(event, str = '') {
            let selection = window.getSelection();
            let text = str || this.getSelectionHTML(selection);
            if (text !== this.lastText && text !== '') { //选择相同文字或空不识别
                let start = performance.now();
                this.lastText = text;
                //util.clog(`当前选中文字：${text}`);
                let linkObj = this.parseLink(text);
                let link = linkObj.link;
                let name = linkObj.name;
                let pwd = this.parsePwd(text);
                if (!link) {
                    linkObj = this.parseParentLink(selection);
                    link = linkObj.link;
                    name = linkObj.name;
                }
                if (link) {
                    if (!/https?:\/\//.test(link)) {
                        link = 'https://' + link;
                    }
                    let end = performance.now();
                    let time = (end - start).toFixed(3);
                    util.clog(`文本识别结果：${name} 链接：${link} 密码：${pwd} 耗时：${time}毫秒`);
                    let option = {
                        toast: true,
                        showCancelButton: true,
                        position: 'top',
                        title: `发现<span style="color: #2778c4;margin: 0 5px;">${name}</span>链接`,
                        html: `<span style="font-size: 0.8em;">${!!pwd ? '密码：' + pwd : '是否打开？'}</span>`,
                        confirmButtonText: '打开',
                        cancelButtonText: '关闭',
                        customClass
                    };
                    if (util.getValue('setting_timer_open')) {
                        option.timer = util.getValue('setting_timer');
                        option.timerProgressBar = true;
                    }
                    util.setValue('setting_success_times', util.getValue('setting_success_times') + 1);

                    Swal.fire(option).then((res) => {
                        this.lastText = 'lorem&';
                        selection.empty();
                        if (res.isConfirmed || res.dismiss === 'timer') {
                            if (linkObj.storage == "local") {
                                util.setValue(linkObj.storagePwdName, pwd);
                            }
                            let active = util.getValue('setting_active_in_front');
                            if (pwd) {
                                let extra = `${link}?pwd=${pwd}#${pwd}`;
                                if (~link.indexOf('?')) {
                                    extra = `${link}&pwd=${pwd}#${pwd}`;
                                }
                                GM_openInTab(extra, {active});
                            } else {
                                GM_openInTab(`${link}`, {active});
                            }
                        }
                    });
                }
            }
        },

        pressKey(event) {
            if (event.key === 'Enter') {
                let confirmBtn = document.querySelector('.panai-container .swal2-confirm');
                confirmBtn && confirmBtn.click();
            }
            if (event.key === 'Escape') {
                let cancelBtn = document.querySelector('.panai-container .swal2-cancel');
                cancelBtn && cancelBtn.click();
            }
        },

        addHotKey() {
            //获取设置中的快捷键
            let hotkey = util.getValue('setting_hotkeys');
            hotkeys(hotkey, (event, handler) => {
                event.preventDefault();
                this.showIdentifyBox();
            });
        },

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

        //正则解析超链接类型网盘链接
        parseParentLink(selection) {
            const dom = this.getSelectionHTML(selection, true).querySelector('*[href]');
            return this.parseLink(dom ? dom.href : "");
        },

        //正则解析提取码
        parsePwd(text) {
            text = text.replace(/\u200B/g, '').replace('%3A', ":");
            text = text.replace(/(?:本帖)?隐藏的?内容[：:]?/, "");
            let reg = /wss:[a-zA-Z0-9]+|(?<=\s*(?:密|提取|访问|訪問|key|password|pwd|#|\?p=)\s*[码碼]?\s*[：:=]?\s*)[a-zA-Z0-9]{3,8}/i;
            if (reg.test(text)) {
                let match = text.match(reg);
                return match[0];
            }
            return '';
        },

        //根据域名检测网盘类型
        panDetect() {
            let hostname = location.hostname;
            for (let name in opt) {
                let val = opt[name];
                if (val.host.test(hostname)) {
                    return name;
                }
            }
            return '';
        },

        //自动填写密码
        autoFillPassword() {
            let query = util.parseQuery('pwd|p');
            let hash = location.hash.slice(1).replace(/\W/g, "") //hash中可能存在密码，需要过滤掉非密码字符
            let pwd = query || hash;
            let panType = this.panDetect();
            for (let name in opt) {
                let val = opt[name];
                if (panType === name) {
                    if (val.storage === 'local') {
                        //当前local存储的密码不一定是当前链接的密码，用户可能通过url直接访问或者恢复页面，这样取出来的密码可能是其他链接的
                        //如果能从url中获取到密码，则应该优先使用url中获取的密码
                        //util.getValue查询不到key时，默认返回undefined，已经形成逻辑短路，此处赋空值无效也无需赋空值.详见https://github.com/syhyz1990/panAI/commit/efb6ff0c77972920b26617bb836a2e19dd14a749
                        pwd = pwd || util.getValue(val.storagePwdName);
                        pwd && this.doFillAction(val.input, val.button, pwd);
                    }
                    if (val.storage === 'hash') {
                        if (!/^(?:wss:[a-zA-Z\d]+|[a-zA-Z0-9]{3,8})$/.test(pwd)) { //过滤掉不正常的Hash
                            return;
                        }
                        pwd && this.doFillAction(val.input, val.button, pwd);
                    }
                }
            }
        },

        doFillAction(inputSelector, buttonSelector, pwd) {
            let maxTime = 10;
            let ins = setInterval(async () => {
                maxTime--;
                let input = util.query(inputSelector);
                let button = util.query(buttonSelector);
                if (input && !util.isHidden(input)) {
                    clearInterval(ins);
                    Swal.fire({
                        toast: true,
                        position: 'top',
                        showCancelButton: false,
                        showConfirmButton: false,
                        title: 'AI已识别到密码！正自动帮您填写',
                        icon: 'success',
                        timer: 2000,
                        customClass
                    });

                    let lastValue = input.value;
                    input.value = pwd;
                    //Vue & React 触发 input 事件
                    let event = new Event('input', {bubbles: true});
                    let tracker = input._valueTracker;
                    if (tracker) {
                        tracker.setValue(lastValue);
                    }
                    input.dispatchEvent(event);

                    if (util.getValue('setting_auto_click_btn')) {
                        await util.sleep(1000); //1秒后点击按钮
                        button.click();
                    }
                } else {
                    maxTime === 0 && clearInterval(ins);
                }
            }, 800);
        },

        //重置识别次数
        clearIdentifyTimes() {
            let res = Swal.fire({
                showCancelButton: true,
                title: '确定要重置识别次数吗？',
                icon: 'warning',
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                customClass
            }).then(res => {
                this.lastText = 'lorem&';
                if (res.isConfirmed) {
                    util.setValue('setting_success_times', 0);
                    history.go(0);
                }
            });
        },

        //识别输入框中的内容
        showIdentifyBox() {
            Swal.fire({
                title: '识别剪切板中文字',
                input: 'textarea',
                inputPlaceholder: '若选方式一，请按 Ctrl+V 粘贴要识别的文字',
                html: `<div style="font-size: 12px;color: #999;margin-bottom: 8px;text-align: center;">提示：在任意网页按下 <span style="font-weight: 700;">${util.getValue("setting_hotkeys")}</span> 键可快速打开本窗口。</div><div style="font-size: 14px;line-height: 22px;padding: 10px 0 5px;text-align: left;"><div style="font-size: 16px;margin-bottom: 8px;font-weight: 700;">支持以下两种方式：</div><div><b>方式一：</b>直接粘贴文字到输入框，点击“识别方框内容”按钮。</div><div><b>方式二：</b>点击“读取剪切板”按钮。<span style="color: #d14529;font-size: 12px;">会弹出“授予网站读取剪切板”权限，同意后会自动识别剪切板中的文字。</span></div></div>`,
                showCloseButton: false,
                showDenyButton: true,
                confirmButtonText: '识别方框内容',
                denyButtonText: '读取剪切板',
                customClass
            }).then(res => {
                if (res.isConfirmed) {
                    this.smartIdentify(null, res.value);
                }
                if (res.isDenied) {
                    navigator.clipboard.readText().then(text => {
                        this.smartIdentify(null, text);
                    }).catch(() => {
                        toast.fire({title: '读取剪切板失败，请先授权或手动粘贴后识别！', icon: 'error'});
                    });
                }
            });
        },

        //显示设置
        showSettingBox() {
            let html = `<div style="font-size: 1em;">
                              <label class="panai-setting-label">填写密码后自动提交<input type="checkbox" id="S-Auto" ${util.getValue('setting_auto_click_btn') ? 'checked' : ''} class="panai-setting-checkbox"></label>
                              <label class="panai-setting-label">前台打开网盘标签页<input type="checkbox" id="S-Active" ${util.getValue('setting_active_in_front') ? 'checked' : ''}
                              class="panai-setting-checkbox"></label>
                              <label class="panai-setting-label">倒计时结束自动打开<input type="checkbox" id="S-Timer-Open" ${util.getValue('setting_timer_open') ? 'checked' : ''} class="panai-setting-checkbox"></label>
                              <label class="panai-setting-label" id="Panai-Range-Wrapper" style="${util.getValue('setting_timer_open') ? '' : 'display: none'}"><span>倒计时 <span id="Timer-Value">（${util.getValue('setting_timer') / 1000}秒）</span></span><input type="range" id="S-Timer" min="0" max="10000" step="500" value="${util.getValue('setting_timer')}" style="width: 200px;"></label>
                              <label class="panai-setting-label">快捷键设置<input type="text" id="S-hotkeys" value="${util.getValue('setting_hotkeys')}" style="width: 100px;"></label> 
                            </div>`;
            Swal.fire({
                title: '识别助手配置',
                html,
                icon: 'info',
                showCloseButton: true,
                confirmButtonText: '保存',
                footer: '<div style="text-align: center;font-size: 1em;">点击查看 <a href="https://www.youxiaohou.com/tool/install-panai.html" target="_blank">使用说明</a>，助手免费开源，Powered by <a href="https://www.youxiaohou.com">油小猴</a></div>',
                customClass
            }).then((res) => {
                res.isConfirmed && history.go(0);
            });

            document.getElementById('S-Auto').addEventListener('change', (e) => {
                util.setValue('setting_auto_click_btn', e.target.checked);
            });
            document.getElementById('S-Active').addEventListener('change', (e) => {
                util.setValue('setting_active_in_front', e.target.checked);
            });
            document.getElementById('S-Timer-Open').addEventListener('change', (e) => {
                let rangeWrapper = document.getElementById('Panai-Range-Wrapper');
                e.target.checked ? rangeWrapper.style.display = 'flex' : rangeWrapper.style.display = 'none';
                util.setValue('setting_timer_open', e.target.checked);
            });
            document.getElementById('S-Timer').addEventListener('change', (e) => {
                util.setValue('setting_timer', e.target.value);
                document.getElementById('Timer-Value').innerText = `（${e.target.value / 1000}秒）`;
            });
            document.getElementById('S-hotkeys').addEventListener('change', (e) => {
                util.setValue('setting_hotkeys', e.target.value);
            });
        },

        registerMenuCommand() {
            GM_registerMenuCommand('👀 已识别：' + util.getValue('setting_success_times') + '次', () => {
                this.clearIdentifyTimes();
            });
            GM_registerMenuCommand(`📋️ 识别剪切板中文字（快捷键 ${util.getValue('setting_hotkeys')}）`, () => {
                this.showIdentifyBox();
            });
            GM_registerMenuCommand('⚙️ 设置', () => {
                this.showSettingBox();
            });
        },

        addPluginStyle() {
            let style = `
                .panai-container { z-index: 99999!important }
                .panai-popup { font-size: 14px !important }
                .panai-setting-label { display: flex;align-items: center;justify-content: space-between;padding-top: 20px; }
                .panai-setting-checkbox { width: 16px;height: 16px; }
            `;

            if (document.head) {
                util.addStyle('swal-pub-style', 'style', GM_getResourceText('swalStyle'));
                util.addStyle('panai-style', 'style', style);
            }

            const headObserver = new MutationObserver(() => {
                util.addStyle('swal-pub-style', 'style', GM_getResourceText('swalStyle'));
                util.addStyle('panai-style', 'style', style);
            });
            headObserver.observe(document.head, {childList: true, subtree: true});
        },

        isTopWindow() {
            return window.self === window.top;
        },

        init() {
            this.initValue();
            this.addPluginStyle();
            this.addHotKey();
            this.autoFillPassword();
            this.addPageListener();
            this.isTopWindow() && this.registerMenuCommand();
        },
    };

    main.init();
})();
