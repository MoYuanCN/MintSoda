<template>
  <el-dropdown trigger="click" placement="top-end" :show-timeout="80" @command="switchLocale">
    <button class="locale-switcher-button" :class="{'is-icon-only': !showLabel}" type="button"
            :aria-label="$t('language')" :title="localeLabel(currentLocale())">
      <el-icon class="locale-switcher-icon">
        <Promotion/>
      </el-icon>
      <span v-if="showLabel" class="locale-switcher-label">{{ localeShortLabel(currentLocale()) }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu class="locale-switcher-menu">
        <el-dropdown-item v-for="item in locales" :key="item.code" :command="item.code"
                          :disabled="item.code === currentLocale()" :class="{'is-current': item.code === currentLocale()}">
          <span class="locale-menu-label">{{ item.label }}</span>
          <el-icon v-if="item.code === currentLocale()" class="locale-menu-check">
            <Check/>
          </el-icon>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import {Check, Promotion} from "@element-plus/icons-vue";
import {currentLocale, localeLabel, localeShortLabel, locales, setLocale} from "@/i18n/index.js";

defineProps({
  showLabel: {
    type: Boolean,
    default: false
  }
})

const switchLocale = code => {
  if (code === currentLocale()) return
  setLocale(code)
  window.location.reload()
}
</script>

<style scoped>
.locale-switcher-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: none;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 12px;
  line-height: 1.3;
  box-sizing: border-box;
  transition: color var(--el-transition-duration), border-color var(--el-transition-duration);
}

.locale-switcher-button:hover,
.locale-switcher-button:focus-visible {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  outline: none;
}

.locale-switcher-button.is-icon-only {
  width: auto;
  padding: 10px;
  border-radius: 50%;
}

.locale-switcher-icon {
  flex-shrink: 0;
}

.locale-switcher-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.locale-switcher-menu {
  min-width: 190px;
}

.locale-switcher-menu :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.locale-switcher-menu :deep(.el-dropdown-menu__item.is-current) {
  color: var(--el-color-primary);
}

.locale-menu-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.locale-menu-check {
  flex-shrink: 0;
}
</style>
