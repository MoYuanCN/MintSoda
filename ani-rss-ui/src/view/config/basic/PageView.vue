<template>
  <el-dialog v-model="jsDialogVisible" align-center center :title="$t('customjs')" width="800">
    <el-input v-model:model-value="props.config['customJs']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-link type="primary" href="https://github.com/MoYuanCN/ani-rss-Yuri/tree/main/ani-rss-ui/public/api"
               target="_blank">{{ $t('morecss') }}
      </el-link>
    </div>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="jsDialogVisible = false">{{ $t('close') }}</el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="cssDialogVisible" align-center center :title="$t('customcss')" width="800">
    <el-input v-model:model-value="props.config['customCss']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="cssDialogVisible = false">{{ $t('close') }}</el-button>
    </div>
  </el-dialog>
  <SettingsItem :label="$t('appearance')">
    <el-radio-group v-model="store">
      <el-radio-button :label="$t('auto')" value="auto">
        <template #default>
          <el-icon>
            <Adjust/>
          </el-icon>
        </template>
      </el-radio-button>
      <el-radio-button :label="$t('light')" value="light">
        <el-icon>
          <Sun/>
        </el-icon>
      </el-radio-button>
      <el-radio-button :label="$t('dark')" value="dark">
        <el-icon>
          <Moon/>
        </el-icon>
      </el-radio-button>
    </el-radio-group>
  </SettingsItem>
  <SettingsItem :label="$t('accentcolor')">
    <div class="theme-color-row">
      <el-color-picker v-model="props.config.accentColor" :predefine="predefineColors"
                       @change="colorChange(props.config.accentColor || '#409eff')"/>
      <el-button bg text @click="resetAccentColor">{{ $t('resettodefault') }}</el-button>
    </div>
    <el-checkbox v-model="props.config.themeDynamicColor" :label="$t('pickcolorfrombackground')"/>
    <el-text size="small" type="info">{{ $t('anexplicitaccentcolorwinswhenunsetanddynamic') }}</el-text>
  </SettingsItem>
  <SettingsItem :label="$t('surfacestyle')">
    <div class="theme-preset-row">
      <el-button v-for="preset in qualityPresets" :key="preset.name" bg text
                 @click="applyPreset(preset)">{{ preset.name }}</el-button>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('appbackground')">
    <div class="theme-slider-pair">
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
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">{{ $t('lightmode') }}</el-text>
        <UiAssetPicker :config="props.config" field="loginBackgroundImage"/>
      </div>
      <div>
        <el-text size="small">{{ $t('darkmodeblankreuseslight') }}</el-text>
        <UiAssetPicker :config="props.config" field="loginBackgroundImageDark"/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('backgroundeffect')">
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">{{ $t('blur') }}</el-text>
        <el-slider v-model="props.config.backgroundBlur" :min="0" :max="40" show-input/>
      </div>
      <div>
        <el-text size="small">{{ $t('opacity') }}</el-text>
        <el-slider v-model="props.config.backgroundOpacity" :min="0" :max="100" show-input/>
      </div>
    </div>
    <el-text size="small" type="info">{{ $t('blurandopacityonlyaffectthebackgroundimageno') }}</el-text>
  </SettingsItem>
  <SettingsItem :label="$t('loginlogo')">
    <UiAssetPicker :config="props.config" field="loginLogoImage"/>
  </SettingsItem>
  <SettingsItem :label="$t('applogo')">
    <UiAssetPicker :config="props.config" field="appLogoImage"/>
  </SettingsItem>
  <SettingsItem :label="$t('cardopacity')">
    <el-slider v-model="props.config.cardOpacity" :min="0" :max="100" show-input/>
  </SettingsItem>
  <SettingsItem :label="$t('cardblur')">
    <el-slider v-model="props.config.cardBlur" :min="0" :max="40" show-input/>
  </SettingsItem>
  <SettingsItem :label="$t('navigationbar')">
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">{{ $t('opacity') }}</el-text>
        <el-slider v-model="props.config.navOpacity" :min="0" :max="100" show-input/>
      </div>
      <div>
        <el-text size="small">{{ $t('blur') }}</el-text>
        <el-slider v-model="props.config.navBlur" :min="0" :max="40" show-input/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('dialog')">
    <div class="theme-slider-pair">
      <div>
        <el-text size="small">{{ $t('opacity') }}</el-text>
        <el-slider v-model="props.config.dialogOpacity" :min="0" :max="100" show-input/>
      </div>
      <div>
        <el-text size="small">{{ $t('blur') }}</el-text>
        <el-slider v-model="props.config.dialogBlur" :min="0" :max="40" show-input/>
      </div>
    </div>
  </SettingsItem>
  <SettingsItem :label="$t('cornerradius')">
    <el-slider v-model="props.config.appCornerRadius" :min="0" :max="40" show-input/>
  </SettingsItem>
  <SettingsItem :label="$t('defaultsort')">
    <el-select v-model="props.config['sortType']" class="width-200">
      <el-option value="SCORE" :label="$t('score')"/>
      <el-option value="PINYIN" :label="$t('pinyin')"/>
      <el-option value="TITLE" :label="$t('title')"/>
      <el-option value="DOWNLOAD_TIME" :label="$t('updatedat')"/>
      <el-option value="UPDATE_WEEK" :label="$t('updateweekday')"/>
      <el-option value="STANDBY_RSS" :label="$t('standbyrsscount')"/>
    </el-select>
    <el-text size="small" type="info">{{ $t('thesubscriptionspagecanswitchsortingtemporar') }}</el-text>
  </SettingsItem>
  <SettingsItem :label="$t('oncoverclick')">
    <el-select v-model="coverClickAction" class="width-150">
      <el-option :label="$t('editsubscription')" value="edit"/>
      <el-option :label="$t('editcover')" value="cover"/>
    </el-select>
  </SettingsItem>
  <SettingsItem :label="$t('startuppage')">
    <el-select v-model="startupPage" class="width-150">
      <el-option :label="$t('home')" value="/home"/>
      <el-option :label="$t('subscriptionspage')" value="/subscriptions"/>
    </el-select>
  </SettingsItem>
  <SettingsItem :label="$t('maxcontentwidth')">
    <el-input-number v-model="maxContentWidth"
                     :min="1200">
      <template #suffix>
        <span>px</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem :label="$t('other')">
    <el-checkbox v-model="showScore" :label="$t('showscore')"/>
    <el-checkbox v-model="showWeek" :label="$t('groupbyweekday')"/>
    <el-checkbox v-model="showLastDownloadTime" :label="$t('showlastupdatetime')"/>
  </SettingsItem>
  <SettingsItem :label="$t('custom')">
    <el-button bg @click="jsDialogVisible = true">
      <template #icon>
        <Js/>
      </template>
      JavaScript
    </el-button>
    <el-button bg @click="cssDialogVisible = true">
      <template #icon>
        <Css3Alt/>
      </template>
      CSS
    </el-button>
  </SettingsItem>
  <SettingsItem label="WebUI">
    <UploadView url="api/webui/upload" :callback="callback" :extensions="['zip']">
      <el-button bg icon="Upload">{{ $t('chooseafileandupload') }}</el-button>
    </UploadView>
  </SettingsItem>
</template>

<script setup>
import {useI18n} from "vue-i18n"
import {ref} from "vue";
import {Adjust, Css3Alt, Js, Moon, Sun} from "@vicons/fa";
import {
  colorChange,
  coverClickAction,
  maxContentWidth,
  showLastDownloadTime,
  showScore,
  showWeek,
  startupPage,
  store,
} from "@/js/global.js";
import {ElMessage} from "element-plus";
import UploadView from "@/view/custom/UploadView.vue";
import SettingsItem from "@/view/custom/SettingsItem.vue";
import UiAssetPicker from "@/view/config/UiAssetPicker.vue";
import {watch} from "vue";
import {applyUiCustomization} from "@/js/global.js";
const {t} = useI18n()
let predefineColors = ref([
  '#409eff', '#109D58', '#BF3545', '#CB7574',
  '#9AAEC7', '#2EC5B6', '#1C1C1C', '#F7B1A9',
  '#B18874', '#E9BA86', '#F68F6C', '#F0458B',
  '#C35653', '#40494E', '#6F0000', '#8D3647',
  '#E6C5D0', '#2377B3', '#49312D', '#7C9AB6',
  '#A5B18D', '#E8662A', '#AB5D50',
  // 浅色清新主题
  '#6FA8DC', '#F4A6B8', '#7BC8A4', '#F6C177',
  '#9DBFEF', '#E8A0C8', '#8ED1C4', '#B7A6E8'
])

// 浅色清新主题预设：只修改已有的外观字段，不新增配置项、不产生额外文件。
const qualityPresets = [
  {
    name: t('blueglass'),
    accentColor: '#5B8FF9',
    cardOpacity: 78, cardBlur: 14,
    navOpacity: 72, navBlur: 18,
    dialogOpacity: 82, dialogBlur: 16,
    appCornerRadius: 16
  },
  {
    name: t('sakurapink'),
    accentColor: '#E48FB2',
    cardOpacity: 82, cardBlur: 18,
    navOpacity: 78, navBlur: 20,
    dialogOpacity: 86, dialogBlur: 18,
    appCornerRadius: 18
  },
  {
    name: t('mint'),
    accentColor: '#4FB8A8',
    cardOpacity: 88, cardBlur: 10,
    navOpacity: 84, navBlur: 12,
    dialogOpacity: 92, dialogBlur: 10,
    appCornerRadius: 12
  },
  {
    name: t('frosted'),
    accentColor: '',
    cardOpacity: 88, cardBlur: 24,
    navOpacity: 86, navBlur: 24,
    dialogOpacity: 90, dialogBlur: 24,
    appCornerRadius: 10
  },
  {
    name: t('minimalclear'),
    accentColor: '#409eff',
    cardOpacity: 96, cardBlur: 2,
    navOpacity: 96, navBlur: 2,
    dialogOpacity: 100, dialogBlur: 0,
    appCornerRadius: 8
  }
]

const applyPreset = preset => {
  Object.keys(preset).forEach(key => {
    if (key !== 'name') props.config[key] = preset[key]
  })
  // 预设里的 accentColor 为空时交回动态取色或默认色。
  props.config.themeDynamicColor = false
  applyUiCustomization(props.config)
}

const resetAccentColor = () => {
  props.config.accentColor = ''
  props.config.themeDynamicColor = false
  colorChange('#409eff')
}

let jsDialogVisible = ref(false)
let cssDialogVisible = ref(false)

let callback = res => {
  let {code, message} = res
  if (code === 200) {
    ElMessage.success(message)
    return
  }
  ElMessage.error(message)
}

let props = defineProps(['config'])

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
.theme-color-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.theme-preset-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.theme-slider-pair {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  width: 100%;
}

.justify-end {
  justify-content: end;
}

.mt-8 {
  margin-top: 8px;
}
</style>
