//index.ts
import { ObjectDirective, App, Plugin } from "vue";

// 自定义指令 可以引用多个
import vClipboard from "./vClipboard";

// 构建指令集
const directives = [vClipboard];

export default {
    install: (app) => {
        // 安装指令集
        directives.forEach((item) => {
            app.directive(item.name, item.options);
        });
    },
};
