<template>
  <el-popconfirm :title="props.title" @confirm="emit('confirm')" :width="160">
    <template #reference>
      <slot name="reference"/>
    </template>
    <template #actions="{ confirm, cancel }">
      <div class="flex action">
        <div>
          <el-button size="small" @click="cancel" bg text icon="Close">{{ $t('cancel') }}</el-button>
        </div>
        <div>
          <el-button
              type="danger"
              size="small"
              @click="confirm"
              bg text
              icon="Check"
          >
            {{ $t('ok') }}
          </el-button>
        </div>
      </div>
    </template>
  </el-popconfirm>
</template>

<script setup>
let props = defineProps({
  title: String
})
const emit = defineEmits(['confirm'])
</script>

<style scoped>
.action {
  justify-content: space-between;
}
</style>
