<template>
  <el-config-provider
      :locale="zhCn"
      :link="linkConfig"
      :dialog="dialogConfig">
    <LoginView v-if="!authorization"/>
    <MainLayoutView v-else/>
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LoginView from "@/view/LoginView.vue";
import MainLayoutView from "@/view/home/MainLayoutView.vue";
import {authorization, init, applyUiCustomization} from "@/js/global.js";
import {reactive, onMounted} from "vue";
import * as http from "@/js/http.js";

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

onMounted(() => {
  http.publicSettings()
      .then(res => applyUiCustomization(res.data))
      .catch(() => {})
})

</script>
