<template>
  <el-dialog v-model="dialogVisible"
             center
             class="el-dialog-auto-width"
             :title="$t('collectionpreview')">
    <div v-loading="loading">
      <el-table :data="list" height="500"
                size="small"
                scrollbar-always-on
                stripe>
        <el-table-column :label="$t('title')" min-width="400" prop="title"/>
        <el-table-column :label="$t('rename')" min-width="280" prop="reName"/>
        <el-table-column :label="$t('episodes')" prop="episode"/>
        <el-table-column :label="$t('size')" min-width="100" prop="formatSize"/>
      </el-table>
    </div>
    <div v-if="subgroup !== props.data.ani.subgroup && subgroup" style="margin-top:12px;">
      <el-alert :close-text="$t('apply')" show-icon @close="closeAlert">
        <template #title>
          <div class="flex" style="width:100%;justify-content: space-between;">
            <span>
              {{ $t('detectedsubgroup', {subgroup}) }}
            </span>
          </div>
        </template>
      </el-alert>
    </div>
    <div class="action">
      <div>
        <span>{{ $t('nitems', {n: list.length}) }}</span>
      </div>
      <el-button bg icon="Close" text @click="dialogVisible = false">{{ $t('close') }}</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import {ref} from "vue";
import * as http from "@/js/http.js";

let dialogVisible = ref(false)
let loading = ref(false)

let list = ref([])

let subgroup = ref('')

let show = () => {
  subgroup.value = ''
  dialogVisible.value = true
  loading.value = true
  http.previewCollection(props.data)
      .then(res => {
        list.value = res.data
        subgroup.value = getSubgroup()
      })
      .finally(() => {
        loading.value = false
      })
}

let getSubgroup = () => {
  if (!list.value) {
    return ''
  }

  let subgroups = list.value
      .map(item => item['title'])
      .map(item => item.match(/^\[(.+?)]/))
      .filter(item => item)
      .map(item => item[1])

  if (subgroups) {
    return subgroups[0]
  }

  return ''
}

let closeAlert = () => {
  props.data.ani.subgroup = subgroup.value
  show()
}

defineExpose({show})

let props = defineProps(['data'])
</script>
<style scoped>
.action {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
}
</style>
