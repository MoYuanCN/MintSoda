<template>
  <div class="app-shell">
    <main class="app-main">
      <RouterView v-slot="{ Component }">
        <Transition name="view-fade" mode="out-in">
          <KeepAlive>
            <component :is="Component"/>
          </KeepAlive>
        </Transition>
      </RouterView>
    </main>
    <aside class="app-nav">
      <div class="app-brand">
        <img class="app-logo" :src="appLogo" alt="MintSoda">
        <span>MintSoda</span>
      </div>
      <el-menu
          :default-active="activeMenu"
          :ellipsis="false"
          class="app-menu"
          router>
        <el-menu-item index="/home">
          <el-icon>
            <House/>
          </el-icon>
          <span>{{ $t('home') }}</span>
        </el-menu-item>
        <el-menu-item index="/subscriptions">
          <el-icon>
            <Collection/>
          </el-icon>
          <span>{{ $t('subscriptions') }}</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon>
            <Tickets/>
          </el-icon>
          <span>{{ $t('logs') }}</span>
        </el-menu-item>
        <el-menu-item index="/plugins">
          <el-icon>
            <Grid/>
          </el-icon>
          <span>{{ $t('plugins') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon>
            <Setting/>
          </el-icon>
          <span>{{ $t('settings') }}</span>
        </el-menu-item>
      </el-menu>
      <div class="app-locale">
        <LocaleSwitcher class="app-locale-inner" show-label/>
      </div>
    </aside>
  </div>
</template>

<script setup>
import {computed, onMounted} from "vue";
import {RouterView, useRoute} from "vue-router";
import {Collection, Grid, House, Setting, Tickets} from "@element-plus/icons-vue";
import LocaleSwitcher from "@/view/custom/LocaleSwitcher.vue";
import {initLayout, uiCustomization, uiAssetUrl} from "@/js/global.js";

const route = useRoute()
// 插件页面挂在 /plugins/:pluginId/:pageId，导航仍高亮“插件”。
const activeMenu = computed(() => route.path.startsWith('/plugins') ? '/plugins' : route.path)
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
  line-height: 1.25;
  padding: 6px 8px !important;
  text-align: center;
}

.app-menu :deep(.el-menu-item .el-icon) {
  margin: 0;
}

/* 英文等较长文案在窄侧栏内换行，避免撑破布局。 */
.app-menu :deep(.el-menu-item span) {
  white-space: normal;
  word-break: break-word;
  font-size: 13px;
}

.app-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-menu-hover-bg-color);
}

.app-locale {
  flex-shrink: 0;
  padding: 8px 12px 12px;
}

.app-locale-inner {
  width: 100%;
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
    font-size: 11px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .app-locale {
    position: fixed;
    right: 12px;
    bottom: calc(64px + env(safe-area-inset-bottom, 0px));
    z-index: 11;
    padding: 0;
  }

  .app-locale-inner {
    width: auto;
    border-radius: 50%;
    background: var(--el-bg-color-overlay);
    box-shadow: var(--el-box-shadow-light);
  }

  .app-main {
    height: 100%;
  }
}
</style>
