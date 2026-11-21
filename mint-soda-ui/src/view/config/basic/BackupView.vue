<template>
  <SettingsItem :label="$t('autobackupsettings')">
    <div style="display: flex;gap: 4px;">
      <el-switch v-model="props.config['configBackup']"/>
      <el-input-number v-model="props.config['configBackupDay']" :min="1">
        <template #suffix>
          <span>{{ $t('days') }}</span>
        </template>
      </el-input-number>
    </div>
  </SettingsItem>
  <div class="content flex">
    <el-button bg @click="exportBackup" icon="Upload">{{ $t('exportsettings') }}</el-button>
    <el-button bg @click="importBackup" icon="Download">{{ $t('importsettings') }}</el-button>
    <UploadView ref="uploadRef" url="api/importBackup" :extensions="['zip']" :callback="callback"/>
  </div>
</template>
<script setup>
import {useI18n} from "vue-i18n"
import {authorization} from "@/js/global.js";
import {ElMessage, ElMessageBox} from "element-plus";
import {markRaw, ref} from "vue";
import {WarnTriangleFilled} from "@element-plus/icons-vue";
import UploadView from "@/view/custom/UploadView.vue";
import SettingsItem from "@/view/custom/SettingsItem.vue";
const {t} = useI18n()
let uploadRef = ref()

let importBackup = () => {
  ElMessageBox.confirm(
      `<strong style="color: var(--el-color-danger);">
        {{ $t('thisoverwritesexistingsettingssubscriptionsanddownloadrecords') }}
       </strong>`,
      t('warning'),
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: t('continue'),
        confirmButtonClass: 'is-text is-has-bg el-button--danger',
        cancelButtonText: t('cancel'),
        cancelButtonClass: 'is-text is-has-bg',
        type: 'warning',
        icon: markRaw(WarnTriangleFilled),
      }
  )
      .then(() => {
        uploadRef.value.selectAndUpload();
      })
}

let callback = res => {
  let {code, message} = res
  if (code !== 200) {
    ElMessage.error(message)
    return
  }
  ElMessage.success(message)
  setTimeout(() => {
    location.reload();
  }, 1000)
}

let exportBackup = () => {
  let element = document.createElement('a');
  element.href = `api/exportBackup?s=${authorization.value}`

  document.body.appendChild(element);

  element.click();

  document.body.removeChild(element);
}

let props = defineProps(['config'])
</script>
<style scoped>
.content {
  width: 100%;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}
</style>
