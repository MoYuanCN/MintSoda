<template>
  <div class="theme-preview">
    <div class="theme-preview-head">
      <span class="theme-preview-title">{{ $t('themepreview') }}</span>
      <el-text size="small" type="info">{{ $t('previewhint') }}</el-text>
    </div>
    <div class="theme-preview-stage">
      <div class="theme-preview-nav">
        <span class="theme-preview-logo">{{ $t('mintsoda') }}</span>
        <span class="theme-preview-nav-item is-active">{{ $t('home') }}</span>
        <span class="theme-preview-nav-item">{{ $t('subscriptionspage') }}</span>
        <span class="theme-preview-nav-item">{{ $t('settings') }}</span>
      </div>
      <div class="theme-preview-main">
        <div class="theme-preview-card">
          <div class="theme-preview-card-title">{{ $t('previewcard') }}</div>
          <div class="theme-preview-card-text">{{ $t('previewtext') }}</div>
          <div class="theme-preview-card-actions">
            <span class="theme-preview-button">{{ $t('previewbutton') }}</span>
            <span class="theme-preview-button is-plain">{{ $t('preview') }}</span>
          </div>
        </div>
        <div class="theme-preview-dialog">
          <div class="theme-preview-dialog-title">{{ $t('previewdialog') }}</div>
          <div class="theme-preview-dialog-text">{{ $t('previewtext') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 预览直接读取根节点上的主题变量，改动滑杆时与真实界面同步变化。
</script>

<style scoped>
.theme-preview {
  position: sticky;
  top: 0;
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: calc(var(--app-corner-radius) * .8);
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
}

.theme-preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.theme-preview-title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 650;
}

.theme-preview-stage {
  position: relative;
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  height: 208px;
  padding: 10px;
  overflow: hidden;
  border-radius: calc(var(--app-corner-radius) * .7);
  background-color: var(--el-fill-color-light);
  background-image: var(--app-background-image);
  background-size: cover;
  background-position: center;
}

.theme-preview-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 8px;
  border-radius: calc(var(--app-corner-radius) * .6);
  background: color-mix(in srgb, var(--el-bg-color) var(--app-nav-opacity), transparent);
  backdrop-filter: blur(var(--app-nav-blur));
}

.theme-preview-logo {
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.theme-preview-nav-item {
  padding: 5px 6px;
  border-radius: 5px;
  color: var(--el-text-color-regular);
  font-size: 10px;
  text-align: center;
}

.theme-preview-nav-item.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.theme-preview-main {
  position: relative;
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.theme-preview-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: calc(var(--app-corner-radius) * .5);
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
  backdrop-filter: blur(var(--app-card-blur));
  box-shadow: var(--el-box-shadow-lighter);
}

.theme-preview-card-title {
  color: var(--el-text-color-primary);
  font-size: 11px;
  font-weight: 600;
}

.theme-preview-card-text {
  color: var(--el-text-color-secondary);
  font-size: 10px;
  line-height: 1.4;
}

.theme-preview-card-actions {
  display: flex;
  gap: 6px;
}

.theme-preview-button {
  padding: 3px 10px;
  border-radius: 4px;
  color: #fff;
  background: var(--el-color-primary);
  font-size: 10px;
}

.theme-preview-button.is-plain {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.theme-preview-dialog {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: calc(var(--app-corner-radius) * .5);
  background: color-mix(in srgb, var(--el-bg-color) var(--app-dialog-opacity), transparent);
  backdrop-filter: blur(var(--app-dialog-blur));
  box-shadow: var(--el-box-shadow-light);
}

.theme-preview-dialog-title {
  color: var(--el-text-color-primary);
  font-size: 10px;
  font-weight: 600;
}

.theme-preview-dialog-text {
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

@media (max-width: 1100px) {
  .theme-preview {
    position: static;
  }
}

@media (max-width: 700px) {
  .theme-preview {
    padding: 12px;
  }

  /* 窄屏优先露出设置项，预览压缩到一张卡片的高度。 */
  .theme-preview-stage {
    grid-template-columns: 72px minmax(0, 1fr);
    height: 150px;
    padding: 8px;
  }

  .theme-preview-nav {
    padding: 6px 4px;
    gap: 4px;
  }

  .theme-preview-nav-item {
    font-size: 9px;
  }

  .theme-preview-dialog {
    display: none;
  }
}
</style>
