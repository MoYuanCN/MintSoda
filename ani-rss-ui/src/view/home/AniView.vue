<template>
  <PreviewView ref="previewRef" :ani="props.ani"/>
  <AniBTView ref="aniBTRef" fill-mode @callback="mikanCallback"/>
  <MikanView ref="mikanRef" fill-mode @callback="mikanCallback"/>
  <TmdbGroupView ref="tmdbGroupRef" :ani="props.ani"/>
  <div style="padding: 0 12px;">
    <el-tabs v-model="activeName" class="tabs-center">
      <el-tab-pane :label="$t('basic')" name="base" :lazy="true">
        <el-scrollbar height="500" ref="scrollbarRef">
          <el-form @submit.prevent label-width="auto">
            <el-form-item :label="$t('title')">
              <div class="full-width">
                <div>
                  <el-input v-model.trim="props.ani.title" class="full-width"/>
                </div>
                <div class="change-title-button">
                  <el-button :loading="getBgmNameLoading"
                             bg
                             icon="DocumentAdd" text @click="getBgmName">
                    {{ $t('usebangumi') }}
                  </el-button>
                  <el-button @click="props.ani.title = ani.themoviedbName"
                             icon="DocumentAdd"
                             :disabled="props.ani.title === ani.themoviedbName || !ani.themoviedbName.length" bg text>
                    {{ $t('usetmdb') }}
                  </el-button>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="TMDB">
              <div class="flex full-width" style="justify-content: space-between;">
                <div class="el-input is-disabled" style="margin-right: 8px">
                  <div class="el-input__wrapper" tabindex="-1"
                       style="pointer-events: auto;cursor: auto;justify-content: left;padding: 0 11px;">
                    <el-link v-if="props.ani?.tmdb?.id"
                             type="primary"
                             :href="`https://www.themoviedb.org/${props.ani.ova ? 'movie' : 'tv'}/${props.ani.tmdb.id}`"
                             target="_blank">
                      {{ props.ani.themoviedbName }}
                    </el-link>
                    <span v-else>{{ props.ani.themoviedbName }}</span>
                  </div>
                </div>
                <el-button icon="Search" bg text @click="searchThemoviedb"/>
                <el-button icon="Refresh" bg text @click="getThemoviedbName" :loading="getThemoviedbNameLoading"/>
              </div>
            </el-form-item>
            <el-form-item v-if="!props.ani.ova && props.ani.tmdb" :label="$t('episodegroup')">
              <div class="tmdb-group">
                <el-input v-model="props.ani.tmdb['tmdbGroupId']" :placeholder="$t('leaveblankfornoepisodegroup')"/>
                <el-button bg text icon="Menu" @click="tmdbGroupRef?.show"/>
              </div>
            </el-form-item>
            <el-form-item label="BgmUrl">
              <div class="tmdb-group">
                <el-input v-model.trim="props.ani.bgmUrl" placeholder="https://bgm.tv/subject/12345"/>
                <el-button bg text :icon="Link" @click="openUrl(props.ani.bgmUrl)"/>
              </div>
            </el-form-item>
            <el-form-item :label="$t('mainrss-2')">
              <div class="full-width">
                <div class="flex full-width">
                  <el-input v-model:model-value="props.ani.subgroup" style="width: 140px" :placeholder="$t('subgroup')"/>
                  <div style="width: 6px;"></div>
                  <el-input v-model:model-value="props.ani.url" placeholder="https://xxx.xxx"/>
                </div>
                <div style="justify-content: end;" class="flex full-width margin-top-4">
                  <el-button bg text
                             @click="mikanRef?.show(props.ani)">
                    <template #icon>
                      <img src="@/icon/icon-Mikan.png" alt="mikan" class="icon"/>
                    </template>
                  </el-button>
                  <el-button bg text
                             @click="aniBTShow">
                    <template #icon>
                      <img src="@/icon/icon-AniBT.png" alt="ani-bt" class="icon"/>
                    </template>
                  </el-button>
                </div>
              </div>
            </el-form-item>
            <el-form-item :label="$t('date')">
              <div class="form-item-flex">
                <el-date-picker
                    style="max-width: 150px;"
                    v-model="props.ani.releaseDate"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                />
              </div>
            </el-form-item>
            <el-form-item :label="$t('season')">
              <div class="form-item-flex">
                <el-input-number style="max-width: 200px"
                                 :min="0"
                                 v-model="props.ani.season"
                                 :disabled="props.ani.ova"/>
              </div>
            </el-form-item>
            <el-form-item :label="$t('episodeoffset')">
              <div class="form-item-flex">
                <el-input-number v-model:model-value="props.ani.offset" :disabled="props.ani.ova"/>
              </div>
            </el-form-item>
            <el-form-item :label="$t('totalepisodes')">
              <div class="form-item-flex">
                <el-input-number v-model:model-value="props.ani.totalEpisodeNumber"/>
              </div>
            </el-form-item>
            <el-form-item :label="$t('match')">
              <ExcludeView v-model:exclude="props.ani.match" :import-exclude="false"/>
            </el-form-item>
            <el-form-item :label="$t('exclude')">
              <ExcludeView v-model:exclude="props.ani.exclude" :import-exclude="true"/>
            </el-form-item>
            <el-form-item :label="$t('globalexclude')">
              <el-switch v-model:model-value="props.ani['globalExclude']"/>
            </el-form-item>
            <el-form-item :label="$t('globalmatch')">
              <el-switch v-model="props.ani.globalMatch"/>
            </el-form-item>
            <el-form-item :label="$t('movie')">
              <el-switch v-model:model-value="props.ani.ova"/>
            </el-form-item>
            <el-form-item :label="$t('enable-3')">
              <el-switch v-model:model-value="props.ani.enable"/>
            </el-form-item>
          </el-form>
        </el-scrollbar>
      </el-tab-pane>
      <el-tab-pane :label="$t('reversematch')" name="reverse-match" :lazy="true">
        <el-scrollbar height="500">
          <div class="reverse-match-panel">
            <el-alert :closable="false" show-icon type="info"
                      :title="$t('filteritemsfromanaggregatedrssbytitlematches')"/>
            <ReverseMatchView :ani="props.ani"/>
          </div>
        </el-scrollbar>
      </el-tab-pane>
      <el-tab-pane :label="$t('standbyrss-2')" name="standby-rss" :lazy="true">
        <StandbyRssView ref="standbyRssRef" :ani="props.ani"/>
      </el-tab-pane>
      <el-tab-pane :label="$t('custom')" name="custom" :lazy="true">
        <el-scrollbar height="500">
          <el-form @submit.prevent label-width="auto">
            <el-form-item :label="$t('customepisoderules')">
              <div class="full-width">
                <div>
                  <el-switch v-model:model-value="props.ani.customEpisode"/>
                </div>
                <div class="flex full-width">
                  <el-input class="full-width"
                            :disabled="!props.ani.customEpisode"
                            v-model:model-value="props.ani.customEpisodeStr"/>
                  <div style="width: 4px;"></div>
                  <el-input-number v-model:model-value="props.ani.customEpisodeGroupIndex"
                                   :disabled="!props.ani.customEpisode"/>
                </div>
              </div>
            </el-form-item>
            <el-form-item :label="$t('custompath')">
              <div class="full-width">
                <div>
                  <el-switch v-model:model-value="props.ani.customDownloadPath"/>
                </div>
                <div>
                  <el-input type="textarea" class="full-width" :disabled="!props.ani.customDownloadPath"
                            :autosize="{ minRows: 2}"
                            v-model:model-value="props.ani.customDownloadPathTemplate"/>
                </div>
                <div style="display: flex;justify-content: space-between;margin-top: 6px;">
                  <el-button :disabled="!props.ani.customDownloadPath" :loading="downloadPathLoading" bg icon="Refresh"
                             text
                             @click="downloadPath"/>
                  <el-text class="mx-1" size="small">
                    {{ $t('thefinaldownloadlocationisdeterminedby') }} <strong>{{ $t('preview') }}</strong>
                  </el-text>
                </div>
              </div>
            </el-form-item>
            <el-form-item :label="$t('customcompletionmigration')">
              <div class="full-width">
                <div>
                  <el-switch v-model="props.ani.customCompleted"/>
                </div>
                <div>
                  <el-input type="textarea" class="full-width" :disabled="!props.ani.customCompleted"
                            :autosize="{ minRows: 2}"
                            v-model:model-value="props.ani.customCompletedPathTemplate"/>
                </div>
              </div>
            </el-form-item>
            <el-form-item :label="$t('renametemplate')">
              <div class="full-width">
                <el-switch v-model="props.ani['customRenameTemplateEnable']"/>
                <br>
                <el-input v-model:model-value="props.ani['customRenameTemplate']"
                          :disabled="!props.ani['customRenameTemplateEnable']"
                          placeholder="${title} S${seasonFormat}E${episodeFormat}"/>
                <br>
                <el-link
                    style="font-size: var(--el-font-size-extra-small)"
                    type="primary"
                    href="https://github.com/MoYuanCN/ani-rss-Yuri/blob/main/docs/USER_GUIDE.md"
                    target="_blank">
                  {{ $t('details') }}
                </el-link>
              </div>
            </el-form-item>
            <el-form-item :label="$t('customtags')">
              <div>
                <el-switch v-model="props.ani.customTagsEnable"/>
                <DisableView v-model="props.ani.customTagsEnable">
                  <CustomTagsView :config="props.ani"/>
                </DisableView>
              </div>
            </el-form-item>
            <el-form-item :label="$t('prioritykeep')">
              <div class="full-width">
                <el-switch v-model="props.ani.customPriorityKeywordsEnable"/>
                <br>
                <DisableView v-model="props.ani.customPriorityKeywordsEnable">
                  <PrioKeysView
                      v-model:keywords="props.ani.customPriorityKeywords"
                      :import-global="true"
                      :show-text="true"
                  />
                </DisableView>
              </div>
            </el-form-item>
            <el-form-item :label="$t('other-2')">
              <el-checkbox v-model="props.ani.omit" :label="$t('missingepisodedetection')"/>
              <el-checkbox v-model="props.ani.downloadNew" :label="$t('onlydownloadthelatestepisode')"/>
              <el-checkbox v-model="props.ani['message']" :label="$t('notifications')"/>
              <el-checkbox v-model="props.ani['completed']" :label="$t('completedmigration')"/>
            </el-form-item>
          </el-form>
        </el-scrollbar>
      </el-tab-pane>
    </el-tabs>
  </div>
  <div class="flex full-width" style="justify-content: space-between;margin-top: 10px;">
    <div>
      <el-dropdown v-if="props.editMode" trigger="click">
        <el-button bg text icon="MoreFilled">
          {{ $t('other') }}
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="refreshAni">
              <el-text>
                <el-icon>
                  <RefreshRight/>
                </el-icon>
                {{ $t('refresh') }}
              </el-text>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div>
      <el-button @click="previewRef.show()" bg text icon="Grid">{{ $t('preview') }}</el-button>
      <el-button icon="Check" type="primary" :loading="okLoading" @click="async ()=>{
        standbyRssRef?.normalize()
        okLoading = true
        emit('callback',()=>okLoading = false)
      }" text bg>{{ $t('ok') }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"

import ExcludeView from "@/view/config/ExcludeView.vue";
import ReverseMatchView from "@/view/config/ReverseMatchView.vue";
import PrioKeysView from "@/view/config/PrioKeysView.vue";
import PreviewView from "./PreviewView.vue";
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox, ElText} from "element-plus";
import StandbyRssView from "./StandbyRssView.vue";
import MikanView from "./MikanView.vue";
import TmdbGroupView from "./TmdbGroupView.vue";
import CustomTagsView from "@/view/config/CustomTagsView.vue";
import {Link, Refresh, RefreshRight} from "@element-plus/icons-vue";
import * as http from "@/js/http.js";
import {getBgmTitle} from "@/js/http.js";
import AniBTView from "@/view/home/AniBTView.vue";
import DisableView from "@/view/custom/DisableView.vue";
const {t} = useI18n()
const activeName = ref('base')

const aniBTRef = ref()
const mikanRef = ref()
const tmdbGroupRef = ref()
const standbyRssRef = ref()

let previewRef = ref()
let okLoading = ref(false)

let getThemoviedbNameLoading = ref(false)

let getThemoviedbName = () => {
  if (!props.ani.title.length) {
    return
  }

  getThemoviedbNameLoading.value = true
  http.getThemoviedbName({
    title: props.ani.title,
    ova: props.ani.ova
  })
      .then(res => {
        ElMessage.success(res.message)
        props.ani['themoviedbName'] = res.data['themoviedbName']
        props.ani['tmdb'] = res.data['tmdb']
      })
      .finally(() => {
        getThemoviedbNameLoading.value = false
      })
}

let searchThemoviedb = () => {
  ElMessageBox.prompt(t('entertmdbid'), {
    confirmButtonText: 'OK',
    confirmButtonClass: 'is-text is-has-bg el-button--primary',
    cancelButtonText: 'Cancel',
    cancelButtonClass: 'is-text is-has-bg',
  })
      .then(value => {
        http.getThemoviedbName({
          tmdbId: value.value,
          ova: props.ani.ova
        })
            .then(res => {
              ElMessage.success(res.message)
              props.ani['themoviedbName'] = res.data['themoviedbName']
              props.ani['tmdb'] = res.data['tmdb']
            })
      })
}

onMounted(() => {
  init()
})

let scrollbarRef = ref()

let init = () => {
  scrollbarRef.value.setScrollTop(0)
  activeName.value = 'base'
}

let refreshAni = () => {
  http.refreshAni(props.ani)
      .then(res => {
        ElMessage.success(res.message)
      })
}

let downloadPathLoading = ref(false)
let downloadPath = () => {
  downloadPathLoading.value = true
  let newAni = JSON.parse(JSON.stringify(props.ani))
  newAni.customDownloadPath = false
  http.downloadPath(newAni)
      .then(res => {
        props.ani.customDownloadPathTemplate = res.data.downloadPath
      })
      .finally(() => {
        downloadPathLoading.value = false
      })
}

let getBgmNameLoading = ref(false)

let getBgmName = () => {
  getBgmNameLoading.value = true
  getBgmTitle(props.ani)
      .then(res => {
        props.ani.title = res.data
      })
      .finally(() => {
        getBgmNameLoading.value = false
      })
}

let mikanCallback = v => {
  let {subgroup, match, url} = v
  props.ani.url = url
  props.ani.subgroup = subgroup

  let newMatch = JSON.parse(match).map(s => `{{${subgroup}}}:${s}`)

  // 剔除旧的同字幕组规则
  props.ani.match = props.ani.match.filter(it => it.indexOf(`{{${subgroup}}}:`) !== 0)

  props.ani.match.push(...newMatch)
}

let aniBTShow = () => {
  let bgmUrl = props.ani.bgmUrl;
  aniBTRef.value?.show(bgmUrl)
}

let openUrl = (url) => window.open(url)

let props = defineProps({
  ani: Object,
  editMode: {type: Boolean, default: false}
})
const emit = defineEmits(['callback'])
</script>

<style scoped>
.change-title-button {
  width: 100%;
  justify-content: end;
  display: flex;
  margin-top: 12px;
}

.tmdb-group {
  display: flex;
  width: 100%;
  justify-content: space-between;
  gap: 8px;
}

.form-item-flex {
  width: 100%;
  display: flex;
  justify-content: end;
}

.icon {
  width: 24px;
  height: 24px;
  border-radius: 8px;
}

.reverse-match-panel {
  display: grid;
  gap: 14px;
  padding: 12px;
}
</style>
