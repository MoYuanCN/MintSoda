<template>
  <div class="ui-asset-picker">
    <UploadView url="api/uiAsset/upload" :extensions="['png', 'jpg', 'jpeg', 'webp', 'gif']"
                :callback="uploadDone">
      <el-button bg icon="Upload">{{ $t('uploadimage') }}</el-button>
    </UploadView>
    <el-button v-if="path" bg text type="danger" icon="Delete" @click="props.config[props.field] = ''">
      {{ $t('remove') }}
    </el-button>
    <el-image v-if="path" :src="assetUrl" fit="cover" class="ui-asset-preview"/>
    <el-text v-else size="small" type="info">{{ $t('notset') }}</el-text>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {computed} from 'vue'
import {ElMessage} from 'element-plus'
import UploadView from '@/view/custom/UploadView.vue'
import {uiAssetUrl} from '@/js/global.js'
const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}, field: {type: String, required: true}})
const path = computed(() => props.config[props.field] || '')
const assetUrl = computed(() => uiAssetUrl(path.value))

const uploadDone = response => {
  if (response.code !== 200) {
    ElMessage.error(response.message || t('imageuploadfailed'))
    return
  }
  props.config[props.field] = response.data
  ElMessage.success(t('imageuploadedsavesettingstoapply'))
}
</script>

<style scoped>
.ui-asset-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ui-asset-preview {
  width: 92px;
  height: 58px;
  border-radius: 6px;
}
</style>
