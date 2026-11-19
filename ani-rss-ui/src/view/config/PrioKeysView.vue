<template>
  <el-dialog :title="$t('keywordsettings')" v-if="add" v-model:model-value="add" center align-center width="300"
             @open="$nextTick(() => $refs.keywordInputRef?.focus())">
    <form @submit.prevent="addKeyword">
      <SettingsItem :label="$t('keyword')">
        <el-input ref="keywordInputRef" :placeholder="$t('egchscht1080p')" v-model="keyword"
                  @keyup.enter="addKeyword"/>
      </SettingsItem>
    </form>
    <div class="flex prio-keys-dialog-footer">
      <el-button bg text @click="addKeyword" icon="Plus">{{ $t('add') }}</el-button>
    </div>
  </el-dialog>
  <div class="full-width">
    <div class="gap-2">
      <el-tag v-if="!props.keywords.length" type="info" class="prio-keys-tag">
        {{ $t('none') }}
      </el-tag>
      <el-tag v-for="(tag, index) in props.keywords" :key="tag" closable :disable-translations="false"
              @close="handleClose(index)" class="prio-keys-tag">
        <el-tooltip :content="t('priorityn', {n: index + 1})">
          <el-text line-clamp="1" size="small" class="prio-keys-tag-text">
            {{ tag }}
          </el-text>
        </el-tooltip>
      </el-tag>
      <el-button bg icon="Plus" size="small" class="prio-keys-tag" text
                 @click="() => add = true"/>
      <el-button v-if="props.keywords.length" bg icon="Delete" size="small"
                 class="prio-keys-delete-button" text type="danger"
                 @click="() => props.keywords.length = 0"/>
    </div>
    <div class="flex prio-keys-footer">
      <el-button bg text size="small" @click="importGlobalKeywords" v-if="props.importGlobal"
                 :disabled="disabledImport" :loading="importLoading" icon="Download">
        {{ $t('importkeywordsfromglobal') }}
      </el-button>
      <el-text class="mx-1" size="small" v-if="props.showText">
        {{ $t('whenatorrenthasmultiplefilesthesekeywordspic') }}
      </el-text>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ref} from "vue";
import {ElMessage} from "element-plus";
import {config} from "@/js/http.js";
const {t} = useI18n()
const handleClose = (index) => {
  props.keywords.splice(index, 1)
}

const add = ref(false)
const keyword = ref('')

const addKeyword = () => {
  if (!keyword.value.trim()) {
    ElMessage.error(t('keywordcannotbeempty'))
    return
  }
  if (props.keywords.indexOf(keyword.value.trim()) > -1) {
    ElMessage.error(t('keywordalreadyexists'))
    return
  }
  props.keywords.push(keyword.value.trim())
  keyword.value = ''
  add.value = false
}

let importLoading = ref(false)
let disabledImport = ref(false)

let importGlobalKeywords = () => {
  importLoading.value = true
  config()
      .then(res => {
        disabledImport.value = true
        if (!res.data.priorityKeywords || !res.data.priorityKeywords.length) {
          ElMessage.warning(t('globalprioritykeepkeywordsareempty'))
          return
        }
        for (let keyword of res.data.priorityKeywords) {
          if (props.keywords.indexOf(keyword) > -1) {
            continue
          }
          props.keywords.push(keyword)
        }
        ElMessage.success(t('importsuccessful'))
      })
      .finally(() => {
        importLoading.value = false
      })
}

let props = defineProps({
  keywords: Array,
  importGlobal: Boolean,
  showText: Boolean
})
</script>

<style scoped>
.prio-keys-dialog-footer {
  width: 100%;
  justify-content: end;
  margin-top: 8px;
}

.prio-keys-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.prio-keys-tag-text {
  max-width: 300px;
  color: var(--el-color-primary);
}

.prio-keys-delete-button {
  margin-left: 0;
  margin-bottom: 4px;
}

.prio-keys-footer {
  margin-top: 4px;
  width: 100%;
  justify-content: space-between;
}
</style>
