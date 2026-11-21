<template>
  <el-dialog v-model="downloadPathDialogVisible" align-center center width="300"
             @close="callback" :title="$t('movefiles')">
    <div>
      <strong>
        {{ $t('thedownloadpathchangedmovealreadydownloadedf') }}
      </strong>
      <br>
      <el-text class="mx-1" size="small">
        {{ downloadPath }}
      </el-text>
    </div>
    <div class="action">
      <el-button icon="Check" text bg type="danger" @click="()=>{
        move = true
        editAni()
        downloadPathDialogVisible = false
      }">{{ $t('move') }}
      </el-button>
      <el-button icon="Close" bg text @click="()=>{
        move = false
        editAni()
        downloadPathDialogVisible = false
      }">{{ $t('donotmove') }}
      </el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="dialogVisible" :title="$t('editsubscription-2')" center v-if="dialogVisible">
    <AniView v-model:ani="ani" edit-mode @callback="editChange"/>
  </el-dialog>
</template>

<script setup>
import {useI18n} from "vue-i18n"

import {ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import AniView from "./AniView.vue";
import {aniData} from "@/js/ani.js";
import * as http from '@/js/http.js'
const {t} = useI18n()
const dialogVisible = ref(false)
const downloadPathDialogVisible = ref(false)

const ani = ref(aniData)

let move = ref(false)
let downloadPath = ref('')
let callback = ref(() => {
})

const editChange = async (fun) => {
  callback.value = fun
  let req = await http.downloadPath(ani.value)
  downloadPath.value = req.data.downloadPath
  if (req.data.change) {
    downloadPathDialogVisible.value = true
    return
  }
  editAni()
}

const editAni = () => {
  let action = () => http.setAni(move.value, ani.value)
      .then(res => {
        ElMessage.success(res.message)
        window.$reLoadList?.()
        dialogVisible.value = false
      })
      .finally(callback.value)

  if (!move.value) {
    action()
    return
  }

  ElMessageBox.confirm(
      t('theentirefolderwillbemovedcontinue'),
      t('warning'),
      {
        confirmButtonText: t('moveanyway'),
        confirmButtonClass: 'is-text is-has-bg el-button--danger',
        cancelButtonText: t('cancel'),
        cancelButtonClass: 'is-text is-has-bg',
        type: 'warning',
      }
  )
      .then(action)
}

const show = (item) => {
  ani.value = JSON.parse(JSON.stringify(item))
  ani.value.showDownlaod = true
  move.value = false
  dialogVisible.value = true
}

defineExpose({
  show
})
</script>

<style scoped>
.action {
  width: 100%;
  display: flex;
  justify-content: end;
  margin-top: 8px;
}
</style>
