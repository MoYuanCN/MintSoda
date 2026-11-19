<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="switchLocale">
    <button class="locale-switcher-button" type="button" :aria-label="$t('language')">
      <el-icon>
        <Promotion/>
      </el-icon>
      <span v-if="showLabel" class="locale-switcher-label">{{ localeLabel(currentLocale()) }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="item in locales" :key="item.code" :command="item.code"
                          :disabled="item.code === currentLocale()">
          {{ item.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import {Promotion} from "@element-plus/icons-vue";
import {currentLocale, localeLabel, locales, setLocale} from "@/i18n/index.js";

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
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: none;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 12px;
  line-height: 1.3;
}

.locale-switcher-button:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

.locale-switcher-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
