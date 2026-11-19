<template>
  <BatchAdditionView ref="batchAdditionRef" v-model="batchAdditionDialogVisible" :items="[]" type="other"
                     @done="batchDone"/>
  <AniBTView ref="aniBTRef"/>
  <MikanView ref="mikanRef"/>
  <BgmView ref="bgmRef" @callback="bgmCallback"/>
  <el-dialog v-model="dialogVisible" class="add-subscription-dialog" center :title="$t('addsubscription')"
             :close-on-click-modal="!rssButtonLoading"
             :close-on-press-escape="!rssButtonLoading"
             :show-close="!rssButtonLoading"
  >
    <div class="add-source-step">
      <el-tabs v-model="activeName" class="add-source-tabs tabs-center" tab-position="top">
        <el-tab-pane
            v-for="source in rssSources"
            :key="source.name"
            :label="source.label"
            :name="source.name">
          <div class="source-panel">
            <div class="source-heading">
              <div class="source-identity">
                <img :src="source.icon" :alt="source.label" class="source-icon"/>
                <div>
                  <h3>{{ source.label }}</h3>
                  <el-text size="small" type="info">{{ $t('rsssubscription') }}</el-text>
                </div>
              </div>
              <el-button
                  bg
                  text
                  type="primary"
                  :disabled="rssButtonLoading"
                  @click="source.open">
                <el-icon class="el-icon--left">
                  <Search/>
                </el-icon>
                {{ $t('browse') }} {{ source.label }}
              </el-button>
            </div>
            <el-form class="source-form" label-position="top" @submit.prevent>
              <el-form-item :label="$t('rssurl')">
                <el-input
                    v-model="ani.url"
                    :autosize="{ minRows: 3, maxRows: 5 }"
                    :disabled="rssButtonLoading"
                    :placeholder="source.placeholder"
                    type="textarea"/>
              </el-form-item>
            </el-form>
            <el-alert
                :closable="false"
                show-icon
                :title="$t('useanrssfeedforasingleshowandsubgroupaggrega')"
                type="info"/>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="$t('other')" name="other">
          <div class="source-panel other-source-panel">
            <el-form class="source-form" label-position="top" @submit.prevent>
              <el-form-item :label="$t('animetitle')">
                <div class="title-search-row">
                  <el-input
                      v-model="ani.title"
                      :disabled="rssButtonLoading"
                      :placeholder="$t('required')"/>
                  <el-button
                      :aria-label="$t('searchbangumi')"
                      bg
                      text
                      type="primary"
                      :disabled="rssButtonLoading"
                      :title="$t('searchbangumi')"
                      @click="bgmRef?.show(ani.title)">
                    <el-icon class="el-icon--left">
                      <Search/>
                    </el-icon>
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item :label="$t('bangumiurl')">
                <el-input
                    v-model="ani.bgmUrl"
                    :disabled="rssButtonLoading"
                    placeholder="https://bgm.tv/subject/123456"/>
              </el-form-item>
              <el-form-item :label="$t('rssurl')">
                <el-input
                    v-model="ani.url"
                    :autosize="{ minRows: 3, maxRows: 5 }"
                    :disabled="rssButtonLoading"
                    placeholder="https://example.com/anime.xml"
                    type="textarea"/>
              </el-form-item>
            </el-form>
            <el-alert
                :closable="false"
                show-icon
            :title="$t('qbittorrentwillreceiverssdownloadtasks')"
                type="warning"/>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div class="action">
        <el-button :loading="rssButtonLoading" bg text type="primary" @click="getRss">
          {{ $t('next') }}
          <el-icon class="el-icon--right">
            <ArrowRight/>
          </el-icon>
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ref} from "vue";
import {ElMessage} from "element-plus";
import {ArrowRight, Search} from "@element-plus/icons-vue";
import MikanView from "./MikanView.vue";
import BgmView from "./BgmView.vue";
import BatchAdditionView from './BatchAdditionView.vue'
import {aniData} from "@/js/ani.js";
import AniBTView from "@/view/home/AniBTView.vue";
import {useLocalStorage} from "@vueuse/core";
import mikanIcon from "@/icon/icon-Mikan.png";
import aniBTIcon from "@/icon/icon-AniBT.png";
const {t} = useI18n()
const aniBTRef = ref()
const mikanRef = ref()
const bgmRef = ref()
const batchAdditionRef = ref()
const batchAdditionDialogVisible = ref(false)

const rssSources = [
  {
    name: 'mikan',
    label: 'Mikan',
    icon: mikanIcon,
    placeholder: 'https://mikanani.me/RSS/Bangumi?bangumiId=xxx&subgroupid=xxx',
    open: () => mikanRef.value?.show()
  },
  {
    name: 'ani-bt',
    label: 'AniBT',
    icon: aniBTIcon,
    placeholder: 'https://anibt.net/rss/anime.xml?bgmId=xxx&groupSlug=xxx',
    open: () => aniBTRef.value?.show()
  },
]

const dialogVisible = ref(false)

const ani = ref(aniData)

const rssButtonLoading = ref(false)

// 手动 RSS 也交给统一弹窗串行加载并确认，不再进入 AddView 内部的第二步。
const getRss = async () => {
  if (activeName.value === 'other') {
    if (!ani.value.bgmUrl) {
      ElMessage.error(t('selectthematchingbangumientry'))
      return
    }
  }
  ani.value.type = activeName.value
  batchAdditionDialogVisible.value = true
  await batchAdditionRef.value?.openRequests([JSON.parse(JSON.stringify(ani.value))])
}

const activeName = useLocalStorage('add-active-name', 'mikan')

const show = () => {
  ani.value = JSON.parse(JSON.stringify(aniData))
  if (!['mikan', 'ani-bt', 'other'].includes(activeName.value)) activeName.value = 'mikan'
  dialogVisible.value = true
  rssButtonLoading.value = false
}

let bgmCallback = it => {
  ani.value.title = it['name_cn'] ? it['name_cn'] : it['name']
  ani.value.bgmUrl = it.url
}

let batchDone = () => {
  window.$reLoadList?.()
}

defineExpose({show})
</script>

<style scoped>
.add-source-tabs {
  min-width: 0;
}

.source-panel {
  min-height: 272px;
  padding: 14px 4px 4px;
}

.source-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.source-identity {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.source-identity h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
}

.source-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
}

.source-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.source-form :deep(.el-form-item__label) {
  height: auto;
  padding-bottom: 7px;
  color: var(--el-text-color-regular);
  font-weight: 600;
  line-height: 1.4;
}

.title-search-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  gap: 8px;
}

.title-search-row .el-button {
  width: 40px;
  margin: 0;
}

.other-source-panel {
  min-height: 366px;
}

.action {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.ani-step-back {
  margin: 0 12px 10px;
}

@media (max-width: 600px) {
  .source-panel {
    min-height: 250px;
    padding-top: 10px;
  }

  .source-heading {
    align-items: flex-start;
    gap: 10px;
    margin-bottom: 16px;
  }

  .source-heading .el-button {
    max-width: 172px;
    margin: 0;
  }

  .source-icon {
    width: 36px;
    height: 36px;
  }

  .other-source-panel {
    min-height: 350px;
  }
}
</style>
