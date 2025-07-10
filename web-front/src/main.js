import * as Vue from 'vue'
import App from './App.vue'
import VueClipboard from 'vue-clipboard3'
import DirectiveExtensions from './directive'

import JsonViewer from 'vue3-json-viewer'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import "vue3-json-viewer/dist/index.css";
import './styles/dark/css-vars.css'
import router from './router'

const app = Vue.createApp(App)
app.use(router)


import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

// Import JsonViewer as a Vue.js plugin
app.use(JsonViewer)
app.use(DirectiveExtensions)

// or
// components: {JsonViewer}

app.use(VueClipboard)
app.use(ElementPlus)
app.mount('#app')
