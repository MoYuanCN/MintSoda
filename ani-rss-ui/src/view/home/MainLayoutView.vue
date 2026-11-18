<template>
  <div class="app-shell">
    <main class="app-main">
      <RouterView v-slot="{ Component }">
        <KeepAlive>
          <component :is="Component"/>
        </KeepAlive>
      </RouterView>
    </main>
    <aside class="app-nav">
      <div class="app-brand">
        <img class="app-logo" :src="appLogo" alt="ANI-RSS-Yuri">
        <span>ANI-RSS-Yuri</span>
      </div>
      <el-menu
          :default-active="route.path"
          :ellipsis="false"
          class="app-menu"
          router>
        <el-menu-item index="/home">
          <el-icon>
            <House/>
          </el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/subscriptions">
          <el-icon>
            <Collection/>
          </el-icon>
          <span>订阅</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon>
            <Tickets/>
          </el-icon>
          <span>日志</span>
        </el-menu-item>
        <el-menu-item index="/plugins">
          <el-icon>
            <Grid/>
          </el-icon>
          <span>插件</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon>
            <Setting/>
          </el-icon>
          <span>设置</span>
        </el-menu-item>
      </el-menu>
    </aside>
  </div>
</template>

<script setup>
import {onMounted} from "vue";
import {RouterView, useRoute} from "vue-router";
import {Collection, Grid, House, Setting, Tickets} from "@element-plus/icons-vue";
import {initLayout, uiCustomization, uiAssetUrl} from "@/js/global.js";
import {computed} from "vue";

const route = useRoute()
const appLogo = computed(() => uiCustomization.value.appLogoImage
    ? uiAssetUrl(uiCustomization.value.appLogoImage)
    : '/icon.png')

onMounted(() => {
  initLayout()
})
</script>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
  display: flex;
}

.app-nav {
  width: 132px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  border-left: 1px solid var(--el-border-color-lighter);
  /* 侧栏亚克力需要自行建立层叠上下文，否则模糊会透出下层页面内容。 */
  position: relative;
  z-index: 1;
}

.app-brand {
  height: 80px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.app-logo {
  width: 34px;
  height: 34px;
}

/* 菜单项均分侧栏剩余高度，铺满整列而不是聚在顶部。 */
.app-menu {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 0;
  background: none;
  padding: 8px 12px;
  box-sizing: border-box;
}

.app-menu :deep(.el-menu-item) {
  flex: 1;
  height: auto;
  min-height: 52px;
  margin: 4px 0;
  border-radius: 8px;
  border: none !important;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  line-height: 1.2;
  padding: 0 8px !important;
}

.app-menu :deep(.el-menu-item .el-icon) {
  margin: 0;
}

.app-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-menu-hover-bg-color);
}

.app-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

@media (max-width: 800px) {
  .app-shell {
    display: block;
    padding-bottom: calc(58px + env(safe-area-inset-bottom, 0px));
  }

  .app-nav {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 10;
    width: 100%;
    height: 58px;
    padding-bottom: env(safe-area-inset-bottom, 0px);
    border-top: 1px solid var(--el-border-color-light);
    border-left: none;
  }

  .app-brand {
    display: none;
  }

  .app-menu {
    flex: none;
    height: 58px;
    flex-direction: row;
    justify-content: space-around;
    gap: 4px;
    padding: 4px;
    box-sizing: border-box;
  }

  .app-menu :deep(.el-menu-item) {
    flex: 1;
    height: 50px;
    min-height: 0;
    line-height: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
    margin: 0;
    padding: 0 4px !important;
    transition: color var(--el-transition-duration), background-color var(--el-transition-duration);
  }

  .app-menu :deep(.el-menu-item .el-icon) {
    margin: 0;
  }

  .app-menu :deep(.el-menu-item span) {
    font-size: 12px;
  }

  .app-main {
    height: 100%;
  }
}
</style>
