<template>
  <section class="settings-card">
    <header class="settings-card-head">
      <div class="settings-card-heading">
        <h3 class="settings-card-title">{{ title }}</h3>
        <p v-if="description" class="settings-card-description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="settings-card-actions">
        <slot name="actions"/>
      </div>
    </header>
    <div class="settings-card-body">
      <slot/>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.settings-card {
  min-width: 0;
  padding: 18px 20px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: calc(var(--app-corner-radius) * .8);
  background: color-mix(in srgb, var(--el-bg-color) var(--app-card-opacity), transparent);
}

.settings-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.settings-card-heading {
  min-width: 0;
}

.settings-card-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.4;
}

.settings-card-description {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.settings-card-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 700px) {
  .settings-card {
    padding: 14px 14px 2px;
  }
}
</style>
