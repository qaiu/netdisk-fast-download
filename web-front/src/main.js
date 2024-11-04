import * as Vue from 'vue'
import App from './App.vue'
import VueClipboard from 'vue-clipboard3'
import DirectiveExtensions from './directive'

import JsonViewer from 'vue3-json-viewer'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import "vue3-json-viewer/dist/index.css";

window.$vueApp = Vue.createApp(App)


import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    window.$vueApp.component(key, component)
}

// Import JsonViewer as a Vue.js plugin
window.$vueApp.use(JsonViewer)
window.$vueApp.use(DirectiveExtensions)

// or
// components: {JsonViewer}

window.$vueApp.use(VueClipboard)
window.$vueApp.use(ElementPlus)
window.$vueApp.mount('#app')
