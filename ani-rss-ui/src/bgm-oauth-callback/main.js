import {createApp} from 'vue'
import AppView from './AppView.vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

// 该页面不使用图标，无需注册整套图标。
const app = createApp(AppView)
app.mount('#app')
