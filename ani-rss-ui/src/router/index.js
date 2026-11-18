import {createRouter, createWebHashHistory} from 'vue-router'
import {startupPage} from '@/js/global.js'

// 按路由拆分代码，首屏只加载当前页面。
const DashboardView = () => import('@/view/home/DashboardView.vue')
const SubscriptionView = () => import('@/view/home/SubscriptionView.vue')
const LogsView = () => import('@/view/home/LogsView.vue')
const ConfigView = () => import('@/view/home/ConfigView.vue')

const startupPaths = ['/home', '/subscriptions']

const routes = [
    {
        path: '/',
        redirect: () => startupPaths.includes(startupPage.value) ? startupPage.value : '/home'
    },
    {
        path: '/home',
        component: DashboardView
    },
    {
        path: '/subscriptions',
        component: SubscriptionView
    },
    {
        path: '/logs',
        component: LogsView
    },
    {
        path: '/settings',
        component: ConfigView
    }
]

const router = createRouter({
    history: createWebHashHistory(),
    routes
})

export default router
