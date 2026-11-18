import {createApp} from 'vue'
import MainView from '@/view/MainView.vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import router from '@/router/index.js'
import {globalIcons} from '@/js/icons.js'

const app = createApp(MainView)
// 仅注册模板里按名称使用的图标，其余图标由组件自行按需引入。
for (const [key, component] of Object.entries(globalIcons)) {
    app.component(key, component)
}
app.use(router)
app.mount('#app')
