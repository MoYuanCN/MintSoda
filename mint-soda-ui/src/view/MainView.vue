<template>
  <el-config-provider
      :locale="elementLocale(currentLocale())"
      :link="linkConfig"
      :dialog="dialogConfig">
    <!-- 首屏由占位层遮住，这里直接渲染目标界面，避免过渡依赖离场动画。 -->
    <template v-if="!booting">
      <Transition name="view-fade" mode="out-in">
        <SetupView v-if="setupRequired" key="setup"/>
        <LoginView v-else-if="!authorization" key="login"/>
        <MainLayoutView v-else key="main"/>
      </Transition>
    </template>
  </el-config-provider>
</template>

<script setup>
import LoginView from "@/view/LoginView.vue";
import SetupView from "@/view/SetupView.vue";
import MainLayoutView from "@/view/home/MainLayoutView.vue";
import {authorization, init, applyUiCustomization} from "@/js/global.js";
import {onMounted, reactive, ref} from "vue";
import * as http from "@/js/http.js";
import {currentLocale} from "@/i18n/index.js";
import {elementLocale} from "@/i18n/element.js";

/**
 * 链接配置
 */
let linkConfig = reactive({
  type: 'primary',
  underline: 'never'
})

let dialogConfig = reactive({
  alignCenter: true
})

init()

const setupRequired = ref(false)

// 初始化状态未知时不渲染登录页，避免先闪登录再跳初始化。
const booting = ref(true)

// 首屏占位只在入口 HTML 中存在，界面就绪后淡出移除。
const removeBootSplash = () => {
  const splash = document.querySelector('.boot-splash')
  if (!splash) return
  splash.style.opacity = '0'
  splash.style.transition = 'opacity .18s ease'
  window.setTimeout(() => splash.remove(), 200)
}

const finishBoot = () => {
  booting.value = false
  removeBootSplash()
}

onMounted(() => {
  // 接口异常或响应过慢时也要放行，避免首屏占位一直挡住界面。
  const timeout = window.setTimeout(finishBoot, 5000)
  http.publicSettings()
      .then(res => applyUiCustomization(res.data))
      .catch(() => {})
  // 初始化状态确认后再撤下首屏占位，避免登录页与初始化页之间闪一下。
  http.setupStatus()
      .then(res => {
        setupRequired.value = Boolean(res.data?.required)
      })
      .catch(() => {})
      .finally(() => {
        window.clearTimeout(timeout)
        finishBoot()
      })
})

</script>
