<template>
  <el-switch v-if="field.type === 'SWITCH'"
             :model-value="model[field.key] === 'true'"
             @update:model-value="value => model[field.key] = value ? 'true' : 'false'"/>
  <el-input-number v-else-if="field.type === 'NUMBER'"
                   :model-value="Number(model[field.key] || 0)"
                   @update:model-value="value => model[field.key] = String(value)"/>
  <el-select v-else-if="field.type === 'SELECT'"
             :model-value="model[field.key]"
             @update:model-value="value => model[field.key] = value">
    <el-option v-for="option in field.options || []" :key="option" :label="option" :value="option"/>
  </el-select>
  <el-input v-else
            :model-value="model[field.key] || ''"
            :type="field.type === 'PASSWORD' ? 'password' : 'text'"
            :show-password="field.type === 'PASSWORD'"
            :placeholder="field.placeholder || ''"
            @update:model-value="value => model[field.key] = value"/>
</template>

<script setup>
// 插件字段只支持有限类型，按 type 渲染，避免插件注入任意组件或 HTML。
defineProps({
  field: {type: Object, required: true},
  model: {type: Object, required: true}
})
</script>
