<template>
  <div class="appearance-settings">
    <div class="appearance-controls">
      <SettingsCard :title="$t('themeandcolor')" :description="$t('themeandcolorhint')">
        <SettingsItem :label="$t('thememode')">
          <el-radio-group v-model="store">
            <el-radio-button :label="$t('auto')" value="auto">
              <el-icon><Adjust/></el-icon>
            </el-radio-button>
            <el-radio-button :label="$t('light')" value="light">
              <el-icon><Sun/></el-icon>
            </el-radio-button>
            <el-radio-button :label="$t('dark')" value="dark">
              <el-icon><Moon/></el-icon>
            </el-radio-button>
          </el-radio-group>
        </SettingsItem>
        <SettingsItem :label="$t('accentcolor')">
          <div class="appearance-row">
            <el-color-picker v-model="props.config.accentColor" :predefine="predefineColors"
                             @change="colorChange(props.config.accentColor || '#409eff')"/>
            <el-button bg text @click="resetAccentColor">{{ $t('resettodefault') }}</el-button>
            <el-checkbox v-model="props.config.themeDynamicColor" :label="$t('pickcolorfrombackground')"/>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('surfacestyle')">
          <div class="appearance-presets">
            <button v-for="preset in qualityPresets" :key="preset.name" class="appearance-preset"
                    type="button" @click="applyPreset(preset)">
              <span class="appearance-preset-swatch" :style="{background: preset.preview}"/>
              <span>{{ preset.name }}</span>
            </button>
          </div>
        </SettingsItem>
      </SettingsCard>

      <SettingsCard :title="$t('backgroundandtexture')" :description="$t('backgroundandtexturehint')">
        <SettingsItem :label="$t('appbackground')">
          <div class="appearance-asset-pair">
            <div>
              <el-text size="small">{{ $t('lightmode') }}</el-text>
              <UiAssetPicker :config="props.config" field="appBackgroundImage"/>
            </div>
            <div>
              <el-text size="small">{{ $t('darkmodeblankreuseslight') }}</el-text>
              <UiAssetPicker :config="props.config" field="appBackgroundImageDark"/>
            </div>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('loginbackground')">
          <div class="appearance-asset-pair">
            <div>
              <el-text size="small">{{ $t('lightmode') }}</el-text>
              <UiAssetPicker :config="props.config" field="loginBackgroundImage"/>
            </div>
            <div>
              <el-text size="small">{{ $t('darkmodeblankreuseslight') }}</el-text>
              <UiAssetPicker :config="props.config" field="loginBackgroundImageDark"/>
            </div>
          </div>
          <div class="appearance-asset-actions">
            <el-tooltip :content="$t('inheritfromapphint')" placement="top">
              <span>
                <el-button bg text icon="CopyDocument" :disabled="!props.config.appBackgroundImage"
                           @click="inheritAppBackground">{{ $t('inheritfromapp') }}</el-button>
              </span>
            </el-tooltip>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('backgroundeffect')">
          <div class="appearance-slider-pair">
            <label>
              <span>{{ $t('blur') }}</span>
              <el-slider v-model="props.config.backgroundBlur" :min="0" :max="40" show-input/>
            </label>
            <label>
              <span>{{ $t('opacity') }}</span>
              <el-slider v-model="props.config.backgroundOpacity" :min="0" :max="100" show-input/>
            </label>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('cardstyle')">
          <div class="appearance-slider-pair">
            <label>
              <span>{{ $t('opacity') }}</span>
              <el-slider v-model="props.config.cardOpacity" :min="0" :max="100" show-input/>
            </label>
            <label>
              <span>{{ $t('blur') }}</span>
              <el-slider v-model="props.config.cardBlur" :min="0" :max="40" show-input/>
            </label>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('navigationbar')">
          <div class="appearance-slider-pair">
            <label>
              <span>{{ $t('opacity') }}</span>
              <el-slider v-model="props.config.navOpacity" :min="0" :max="100" show-input/>
            </label>
            <label>
              <span>{{ $t('blur') }}</span>
              <el-slider v-model="props.config.navBlur" :min="0" :max="40" show-input/>
            </label>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('dialog')">
          <div class="appearance-slider-pair">
            <label>
              <span>{{ $t('opacity') }}</span>
              <el-slider v-model="props.config.dialogOpacity" :min="0" :max="100" show-input/>
            </label>
            <label>
              <span>{{ $t('blur') }}</span>
              <el-slider v-model="props.config.dialogBlur" :min="0" :max="40" show-input/>
            </label>
          </div>
        </SettingsItem>
        <SettingsItem :label="$t('cornerradius')">
          <el-slider v-model="props.config.appCornerRadius" :min="0" :max="40" show-input/>
        </SettingsItem>
      </SettingsCard>

      <SettingsCard :title="$t('branding')">
        <SettingsItem :label="$t('loginlogo')">
          <UiAssetPicker :config="props.config" field="loginLogoImage"/>
        </SettingsItem>
        <SettingsItem :label="$t('applogo')">
          <UiAssetPicker :config="props.config" field="appLogoImage"/>
        </SettingsItem>
      </SettingsCard>

      <SettingsCard :title="$t('layoutbehavior')" :description="$t('layoutbehaviorhint')">
        <SettingsItem :label="$t('startuppage')">
          <el-select v-model="startupPage" class="appearance-select">
            <el-option :label="$t('home')" value="/home"/>
            <el-option :label="$t('subscriptionspage')" value="/subscriptions"/>
          </el-select>
        </SettingsItem>
        <SettingsItem :label="$t('defaultsort')">
          <el-select v-model="props.config.sortType" class="appearance-select">
            <el-option value="SCORE" :label="$t('score')"/>
            <el-option value="PINYIN" :label="$t('pinyin')"/>
            <el-option value="TITLE" :label="$t('title')"/>
            <el-option value="DOWNLOAD_TIME" :label="$t('updatedat')"/>
            <el-option value="UPDATE_WEEK" :label="$t('updateweekday')"/>
            <el-option value="STANDBY_RSS" :label="$t('standbyrsscount')"/>
          </el-select>
        </SettingsItem>
        <SettingsItem :label="$t('oncoverclick')">
          <el-select v-model="coverClickAction" class="appearance-select">
            <el-option :label="$t('editsubscription')" value="edit"/>
            <el-option :label="$t('editcover')" value="cover"/>
          </el-select>
        </SettingsItem>
        <SettingsItem :label="$t('maxcontentwidth')">
          <el-input-number v-model="maxContentWidth" :min="1200">
            <template #suffix><span>px</span></template>
          </el-input-number>
        </SettingsItem>
        <SettingsItem :label="$t('displayoptions')">
          <div class="appearance-checkboxes">
            <el-checkbox v-model="showScore" :label="$t('showscore')"/>
            <el-checkbox v-model="showWeek" :label="$t('groupbyweekday')"/>
            <el-checkbox v-model="showLastDownloadTime" :label="$t('showlastupdatetime')"/>
          </div>
        </SettingsItem>
      </SettingsCard>

      <SettingsCard :title="$t('customappearance')" :description="$t('customappearancehint')">
        <SettingsItem :label="$t('custom')">
          <div class="appearance-row">
            <el-button bg @click="jsDialogVisible = true">
              <template #icon><Js/></template>
              JavaScript
            </el-button>
            <el-button bg @click="cssDialogVisible = true">
              <template #icon><Css3Alt/></template>
              CSS
            </el-button>
          </div>
        </SettingsItem>
        <SettingsItem label="WebUI">
          <UploadView url="api/webui/upload" :callback="callback" :extensions="['zip']">
            <el-button bg icon="Upload">{{ $t('chooseafileandupload') }}</el-button>
          </UploadView>
        </SettingsItem>
      </SettingsCard>
    </div>

    <ThemePreview class="appearance-preview"/>

    <el-dialog v-model="jsDialogVisible" align-center center :title="$t('customjs')" width="800">
      <el-input v-model="props.config.customJs" :rows="6" type="textarea"/>
      <div class="appearance-dialog-footer">
        <el-link type="primary" href="https://github.com/MoYuanCN/MintSoda/tree/main/mint-soda-ui/public/api"
                 target="_blank">{{ $t('morecss') }}</el-link>
        <el-button bg icon="Close" text @click="jsDialogVisible = false">{{ $t('close') }}</el-button>
      </div>
    </el-dialog>
    <el-dialog v-model="cssDialogVisible" align-center center :title="$t('customcss')" width="800">
      <el-input v-model="props.config.customCss" :rows="6" type="textarea"/>
      <div class="appearance-dialog-footer">
        <el-button bg icon="Close" text @click="cssDialogVisible = false">{{ $t('close') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ref, watch} from "vue"
import {Adjust, Css3Alt, Js, Moon, Sun} from "@vicons/fa"
import {ElMessage} from "element-plus"
import {
  applyUiCustomization,
  colorChange,
  coverClickAction,
  maxContentWidth,
  showLastDownloadTime,
  showScore,
  showWeek,
  startupPage,
  store
} from "@/js/global.js"
import SettingsItem from "@/view/custom/SettingsItem.vue"
import SettingsCard from "@/view/custom/SettingsCard.vue"
import UiAssetPicker from "@/view/config/UiAssetPicker.vue"
import ThemePreview from "@/view/config/ThemePreview.vue"
import UploadView from "@/view/custom/UploadView.vue"

const {t} = useI18n()
const props = defineProps({config: {type: Object, required: true}})

const predefineColors = ref([
  '#409eff', '#109D58', '#BF3545', '#CB7574',
  '#9AAEC7', '#2EC5B6', '#1C1C1C', '#F7B1A9',
  '#B18874', '#E9BA86', '#F68F6C', '#F0458B',
  '#C35653', '#40494E', '#6F0000', '#8D3647',
  '#E6C5D0', '#2377B3', '#49312D', '#7C9AB6',
  '#A5B18D', '#E8662A', '#AB5D50',
  '#6FA8DC', '#F4A6B8', '#7BC8A4', '#F6C177',
  '#9DBFEF', '#E8A0C8', '#8ED1C4', '#B7A6E8'
])

// 预设只写入已有外观字段，不新增配置项，也不会生成额外文件。
const qualityPresets = [
  {
    name: t('blueglass'), preview: 'linear-gradient(135deg, #E8F1FF, #5B8FF9)',
    accentColor: '#5B8FF9',
    cardOpacity: 78, cardBlur: 14,
    navOpacity: 72, navBlur: 18,
    dialogOpacity: 82, dialogBlur: 16,
    appCornerRadius: 16
  },
  {
    name: t('sakurapink'), preview: 'linear-gradient(135deg, #FFF0F5, #E48FB2)',
    accentColor: '#E48FB2',
    cardOpacity: 82, cardBlur: 18,
    navOpacity: 78, navBlur: 20,
    dialogOpacity: 86, dialogBlur: 18,
    appCornerRadius: 18
  },
  {
    name: t('mint'), preview: 'linear-gradient(135deg, #EAFBF7, #4FB8A8)',
    accentColor: '#4FB8A8',
    cardOpacity: 88, cardBlur: 10,
    navOpacity: 84, navBlur: 12,
    dialogOpacity: 92, dialogBlur: 10,
    appCornerRadius: 12
  },
  {
    name: t('frosted'), preview: 'linear-gradient(135deg, #F2F5F9, #C8D3E0)',
    accentColor: '',
    cardOpacity: 88, cardBlur: 24,
    navOpacity: 86, navBlur: 24,
    dialogOpacity: 90, dialogBlur: 24,
    appCornerRadius: 10
  },
  {
    name: t('minimalclear'), preview: 'linear-gradient(135deg, #FFFFFF, #409EFF)',
    accentColor: '#409eff',
    cardOpacity: 96, cardBlur: 2,
    navOpacity: 96, navBlur: 2,
    dialogOpacity: 100, dialogBlur: 0,
    appCornerRadius: 8
  }
]

const applyPreset = preset => {
  Object.keys(preset).forEach(key => {
    if (key === 'name' || key === 'preview') return
    props.config[key] = preset[key]
  })
  props.config.themeDynamicColor = false
  applyUiCustomization(props.config)
}

// 登录页背景快速继承应用背景：浅色与深色各自复制，登录后无需再传一次图。
const inheritAppBackground = () => {
  props.config.loginBackgroundImage = props.config.appBackgroundImage || ''
  props.config.loginBackgroundImageDark = props.config.appBackgroundImageDark || ''
  applyUiCustomization(props.config)
  ElMessage.success(t('backgroundinherited'))
}

const resetAccentColor = () => {
  props.config.accentColor = ''
  props.config.themeDynamicColor = false
  colorChange('#409eff')
}

const jsDialogVisible = ref(false)
const cssDialogVisible = ref(false)

const callback = res => {
  const {code, message} = res
  if (code === 200) {
    ElMessage.success(message)
    return
  }
  ElMessage.error(message)
}

// 外观字段改动后立即写回 CSS 变量，预览与整个界面同步，不需要先保存。
watch(() => [
  props.config.appBackgroundImage,
  props.config.loginBackgroundImage,
  props.config.appBackgroundImageDark,
  props.config.loginBackgroundImageDark,
  props.config.backgroundBlur,
  props.config.backgroundOpacity,
  props.config.cardOpacity,
  props.config.cardBlur,
  props.config.appCornerRadius,
  props.config.accentColor,
  props.config.themeDynamicColor,
  props.config.navOpacity,
  props.config.navBlur,
  props.config.dialogOpacity,
  props.config.dialogBlur
], () => applyUiCustomization(props.config), {immediate: true})
</script>

<style scoped>
.appearance-settings {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.appearance-controls {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.appearance-preview {
  position: sticky;
  top: 8px;
}

.appearance-row,
.appearance-checkboxes {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.appearance-select {
  width: 200px;
}

.appearance-asset-actions {
  display: flex;
  width: 100%;
  margin-top: 4px;
}

.appearance-presets {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
  gap: 8px;
  width: 100%;
}

.appearance-preset {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: none;
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
  transition: border-color var(--el-transition-duration), color var(--el-transition-duration);
}

.appearance-preset:hover,
.appearance-preset:focus-visible {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  outline: none;
}

.appearance-preset-swatch {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .08);
}

.appearance-asset-pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
  width: 100%;
}

.appearance-slider-pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
  width: 100%;
}

.appearance-slider-pair label {
  display: grid;
  gap: 2px;
}

.appearance-slider-pair span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.appearance-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .appearance-settings {
    grid-template-columns: minmax(0, 1fr);
  }

  .appearance-preview {
    position: static;
    order: -1;
  }
}
</style>
