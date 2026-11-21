<template>
  <BgmView ref="bgmRef" @callback="bgmAdd"/>
  <CollectionPreviewView ref="collectionPreviewRef" v-model:data="data"/>
  <el-dialog v-model="dialogVisible"
             center
             :title="$t('addcollection')">
    <div v-loading="loading" style="height: 500px;">
      <el-scrollbar style="padding: 0 12px;">
        <div>
          <el-form @submit.prevent label-width="auto">
            <el-form-item :label="$t('animetitle')">
              <div class="full-width">
                <div class="flex full-width">
                  <el-input
                      v-model:model-value="data.ani.title"
                      :disabled="rssButtonLoading"
                      :placeholder="$t('required')"
                      @keyup.enter="bgmRef?.show(data.ani.title)"
                  />
                  <div style="width: 4px;"></div>
                  <el-button :disabled="rssButtonLoading" bg icon="Search" text type="primary"
                             @click="bgmRef?.show(data.ani.title)"/>
                </div>
                <div v-if="data.show" class="change-title-button">
                  <el-button :loading="getBgmNameLoading"
                             bg
                             icon="DocumentAdd" text @click="getBgmName">
                    {{ $t('usebangumi') }}
                  </el-button>
                  <el-button :disabled="data.ani.title === data.ani.themoviedbName || !data.ani.themoviedbName.length"
                             bg
                             icon="DocumentAdd"
                             text
                             @click="useTmdbTitle">
                    {{ $t('usetmdb') }}
                  </el-button>
                </div>
              </div>
            </el-form-item>
            <template v-if="data.show">
              <el-form-item label="TMDB">
                <div class="flex full-width" style="justify-content: space-between;">
                  <div class="el-input is-disabled">
                    <div class="el-input__wrapper"
                         style="pointer-events: auto;cursor: auto;justify-content: left;padding: 0 11px;"
                         tabindex="-1">
                      <el-link v-if="data.ani?.tmdb?.id"
                               :href="`https://www.themoviedb.org/${data.ani.ova ? 'movie' : 'tv'}/${data.ani.tmdb.id}`"
                               target="_blank"
                               type="primary">
                        {{ data.ani.themoviedbName }}
                      </el-link>
                      <span v-else>{{ data.ani.themoviedbName }}</span>
                    </div>
                  </div>
                  <div style="width: 4px;"></div>
                  <el-button :loading="getThemoviedbNameLoading" bg icon="Refresh" text @click="getThemoviedbName"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('subgroup')">
                <div class="form-item-flex">
                  <el-input v-model:model-value="data.ani.subgroup" :placeholder="$t('subgroup')" class="width-150"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('season')">
                <div class="form-item-flex">
                  <el-input-number v-model:model-value="data.ani.season" :min="0" class="width-150"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('episodeoffset')">
                <div class="form-item-flex">
                  <el-input-number v-model:model-value="data.ani.offset"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('date')">
                <div class="form-item-flex">
                  <el-date-picker
                      v-model="data.ani.releaseDate"
                      style="max-width: 150px;"
                  />
                </div>
              </el-form-item>
              <el-form-item :label="$t('match')">
                <ExcludeView v-model:exclude="data.ani.match" :import-exclude="false"/>
              </el-form-item>
              <el-form-item :label="$t('exclude')">
                <ExcludeView v-model:exclude="data.ani.exclude" :import-exclude="true"/>
              </el-form-item>
              <el-form-item :label="$t('globalexclude')">
                <el-switch v-model:model-value="data.ani['globalExclude']"/>
              </el-form-item>
              <el-form-item :label="$t('globalmatch')">
                <el-switch v-model="data.ani.globalMatch"/>
              </el-form-item>
              <el-form-item :label="$t('globalreversematch')">
                <el-switch v-model="data.ani.globalReverseMatch"/>
              </el-form-item>
              <el-form-item v-if="data.ani.globalReverseMatch" :label="$t('reversematchmiss')">
                <el-select v-model="data.ani.reverseMatchMissBehavior" class="full-width">
                  <el-option value="SKIP" :label="$t('skipdonotdownload')"/>
                  <el-option value="NORMAL" :label="$t('continuewithnormalrules')"/>
                  <el-option value="ALLOW" :label="$t('allowasis')"/>
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('movie')">
                <el-switch v-model:model-value="data.ani.ova"/>
              </el-form-item>
              <el-form-item :label="$t('customepisoderules')">
                <div class="flex full-width">
                  <el-input v-model:model-value="data.ani.customEpisodeStr"
                            class="full-width"/>
                  <div style="width: 4px;"></div>
                  <el-input-number v-model:model-value="data.ani.customEpisodeGroupIndex"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('downloadlocation')">
                <div class="full-width">
                  <el-input v-model:model-value="data.ani.customDownloadPathTemplate"
                            :autosize="{ minRows: 2}"
                            :disabled="useCollectionPath"
                            class="full-width"
                            type="textarea"
                            @input="pathEdited = true"/>
                  <div class="path-hint">
                    <el-text v-if="useCollectionPath" size="small" type="success">
                      {{ $t('usingthecollectionsavepathfromsettingsthefie') }}
                    </el-text>
                    <el-text v-else size="small" type="info">
                      {{ $t('usedwhennocollectionsavepathissetupdatesauto') }}
                      {{ $t('enteratemplatewithvariablesfixedpathsarereco') }}
                    </el-text>
                  </div>
                </div>
                <div style="margin-top: 6px;">
                  <el-button :loading="downloadPathLoading" bg icon="Refresh"
                             text
                             @click="downloadPath"/>
                </div>
              </el-form-item>
              <el-form-item :label="$t('renametemplate')">
                <div class="full-width">
                  <el-switch v-model="data.ani['customRenameTemplateEnable']"/>
                  <br>
                  <el-input v-model:model-value="data.ani['customRenameTemplate']"
                            :disabled="!data.ani['customRenameTemplateEnable']"
                            placeholder="${title} S${seasonFormat}E${episodeFormat}"/>
                  <br>
                  <el-link
                      style="font-size: var(--el-font-size-extra-small)"
                      type="primary"
                      href="https://github.com/MoYuanCN/MintSoda/blob/main/docs/USER_GUIDE.md"
                      target="_blank">
                    {{ $t('details') }}
                  </el-link>
                </div>
              </el-form-item>
              <el-form-item :label="$t('customtags')">
                <CustomTagsView :config="data.ani"/>
              </el-form-item>
              <el-form-item label="Torrent">
                <el-tag v-if="data.filename" closable @close="clearTorrent">
                  <el-tooltip :content="data.filename">
                    <el-text line-clamp="1" size="small" class="filename">
                      {{ data.filename }}
                    </el-text>
                  </el-tooltip>
                </el-tag>
                <UploadView v-else
                            url="api/uploadAndReadToBase64"
                            :extensions="['torrent']"
                            :callback="uploadCallback">
                  <el-button bg icon="Upload">{{ $t('chooseanduploadtorrent') }}</el-button>
                </UploadView>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </el-scrollbar>
    </div>
    <div class="action">
      <el-button :disabled="!data.filename" bg
                 icon="Grid"
                 text
                 @click="collectionPreviewRef?.show">
        {{ $t('preview') }}
      </el-button>
      <el-button :disabled="!data.filename"
                 :loading="startLoading"
                 bg
                 icon="Check"
                 text
                 type="primary" @click="start">
        {{ $t('start') }}
      </el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ref, watch} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import BgmView from "./BgmView.vue";
import ExcludeView from "@/view/config/ExcludeView.vue";
import CollectionPreviewView from "./CollectionPreviewView.vue";
import CustomTagsView from "@/view/config/CustomTagsView.vue";
import {aniData} from "@/js/ani.js";
import * as http from "@/js/http.js";
import {getBgmTitle} from "@/js/http.js";
import UploadView from "@/view/custom/UploadView.vue";
const {t} = useI18n()
let start = () => {
  startLoading.value = true
  http.startCollection(data.value)
      .then((res) => {
        ElMessageBox.confirm(
            res.message,
            'success',
            {
              confirmButtonText: 'OK',
              confirmButtonClass: 'is-text is-has-bg el-button--primary',
              type: 'success',
              center: true,
              showCancelButton: false
            }
        )
      })
      .finally(() => {
        startLoading.value = false
      })
}

let downloadPathLoading = ref(false)
const useCollectionPath = ref(false)
const pathEdited = ref(false)
let refreshTimer

/**
 * 重新计算合集保存位置。服务端会优先使用设置中的合集保存位置，
 * 因此标题、TMDB、季或剧场版标记变化后都应调用，避免保存到过期路径。
 */
const refreshCollectionPath = (notify = false) => {
  // 立即刷新时取消已排队的自动刷新，避免重复请求。
  clearTimeout(refreshTimer)
  if (!data.value.show) {
    return Promise.resolve()
  }
  downloadPathLoading.value = true
  return http.collectionDownloadPath(data.value.ani)
      .then(res => {
        useCollectionPath.value = Boolean(res.data.useCollectionPath)
        // 始终展示实际会使用的路径；使用合集位置时输入框只读。
        data.value.ani.customDownloadPathTemplate = res.data.downloadPath
        // 自动填入的路径只是展示值，交给服务端按当前信息重新计算。
        data.value.ani.customDownloadPath = pathEdited.value
        pathEdited.value = false
        if (notify) {
          ElMessage.success(t('savepathupdated'))
        }
      })
      .finally(() => {
        downloadPathLoading.value = false
      })
}

let downloadPath = () => refreshCollectionPath(true)

let collectionPreviewRef = ref()

let startLoading = ref(false);

let bgmRef = ref()

let rssButtonLoading = ref(false)
let loading = ref(false)

// 清空已经上传的种子文件及其 Base64 内容。
let clearTorrent = () => {
  data.value.filename = ''
  data.value.torrent = ''
}

let bgmAdd = (bgm) => {
  loading.value = true
  data.value.show = false
  data.value.torrent = ''
  data.value.filename = ''
  http.getAniBySubjectId(bgm['id'])
      .then((res) => {
        data.value.ani = res.data
        data.value.ani.subgroup = t('unknownsubgroup')
        data.value.ani.customEpisode = true
        data.value.show = true
        data.value.ani.match = []
        data.value.ani.exclude = ['^(SPs?|CDs|Scans|PV|menu)/', 'Fonts|NCED|NCOP|Mini anime']
        refreshCollectionPath()
      })
      .finally(() => {
        loading.value = false
      })
}

let uploadCallback = (res, file) => {
  data.value.filename = file.name
  data.value.torrent = res.data
  // 获取字幕组
  http.getCollectionSubgroup(data.value)
      .then(res => {
        data.value.ani.subgroup = res.data
        if (res.data !== t('unknownsubgroup')) {
          ElMessage.success(t('subgroupupdatedto', {subgroup: res.data}))
        }
      })
}

let data = ref({
  filename: '',
  torrent: '',
  ani: aniData,
  show: false,
})

let dialogVisible = ref(false)

let show = () => {
  data.value.show = false
  useCollectionPath.value = false
  pathEdited.value = false
  data.value.ani.title = ''
  data.value.torrent = ''
  data.value.filename = ''
  dialogVisible.value = true
}

let getBgmNameLoading = ref(false)

let getBgmName = () => {
  getBgmNameLoading.value = true
  getBgmTitle(data.value.ani)
      .then(res => {
        data.value.ani.title = res.data
      })
      .finally(() => {
        getBgmNameLoading.value = false
      })
}

let getThemoviedbNameLoading = ref(false)

let getThemoviedbName = () => {
  if (!data.value.ani.title.length) {
    return
  }

  getThemoviedbNameLoading.value = true
  http.getThemoviedbName(data.value.ani)
      .then(res => {
        ElMessage.success(res.message)
        data.value.ani['themoviedbName'] = res.data['themoviedbName']
        data.value.ani['tmdb'] = res.data['tmdb']
      })
      .finally(() => {
        getThemoviedbNameLoading.value = false
      })
}

const useTmdbTitle = () => {
  data.value.ani.title = data.value.ani.themoviedbName
}

// 标题、TMDB、季或剧场版标记变化都会影响保存路径，自动重新计算，避免忘记手动刷新。
const scheduleRefreshPath = () => {
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => refreshCollectionPath(), 400)
}

watch(
    () => [
      data.value.ani.title,
      data.value.ani.themoviedbName,
      data.value.ani.tmdb?.id,
      data.value.ani.subgroup,
      data.value.ani.season,
      data.value.ani.ova,
      data.value.ani.releaseDate
    ],
    () => {
      // 自动刷新会覆盖输入框内容，用户刚改过路径时不再自动重算。
      if (!data.value.show || pathEdited.value) {
        return
      }
      scheduleRefreshPath()
    }
)


defineExpose({show})

</script>

<style scoped>
.change-title-button {
  width: 100%;
  justify-content: end;
  display: flex;
  margin-top: 12px;
}

.form-item-flex {
  width: 100%;
  display: flex;
  justify-content: end;
}

.filename {
  max-width: 300px;
  color: var(--el-color-info);
}

.path-hint {
  margin-top: 4px;
}

.action {
  width: 100%;
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
}
</style>
