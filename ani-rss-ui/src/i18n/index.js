import {createI18n} from 'vue-i18n'
import {useLocalStorage} from '@vueuse/core'
import dayjs from 'dayjs'
import {dayjsLocale} from './element.js'
import zhCn from './locales/zh-CN.js'
import zhTw from './locales/zh-TW.js'
import zhMs from './locales/zh-MS.js'
import enUs from './locales/en-US.js'

/**
 * 内置语言，按顺序展示在语言选择器中。
 */
export const locales = [
  {code: 'zh-CN', label: '简体中文（中国大陆）'},
  {code: 'zh-TW', label: '繁體中文（台灣）'},
  {code: 'zh-MS', label: '简体中文（Microsoft）'},
  {code: 'en-US', label: 'English'}
]

export const locale = useLocalStorage('locale', '')

const normalize = value => {
  const tag = String(value || '').replace('_', '-').toLowerCase()
  if (!tag) return ''
  if (tag.startsWith('zh-ms')) return 'zh-MS'
  if (tag.startsWith('zh-tw') || tag.startsWith('zh-hk') || tag.startsWith('zh-mo') || tag.startsWith('zh-hant')) {
    return 'zh-TW'
  }
  if (tag.startsWith('zh')) return 'zh-CN'
  if (tag.startsWith('en')) return 'en-US'
  return ''
}

const detect = () => {
  const saved = normalize(locale.value)
  if (saved) return saved
  const candidates = typeof navigator === 'undefined'
      ? []
      : [navigator.language, ...(navigator.languages || [])]
  for (const candidate of candidates) {
    const matched = normalize(candidate)
    if (matched) return matched
  }
  return 'zh-CN'
}

const initialLocale = detect()

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCn,
    'zh-TW': zhTw,
    'zh-MS': zhMs,
    'en-US': enUs
  }
})

dayjs.locale(dayjsLocale(initialLocale))

if (typeof document !== 'undefined') {
  document.documentElement.lang = initialLocale === 'zh-TW' ? 'zh-TW' : initialLocale === 'en-US' ? 'en' : 'zh-CN'
}

/**
 * 供脚本与工具函数使用的翻译函数。
 *
 * @param {string} key 文案键
 * @param {object} args 插值参数
 * @returns {string} 文案
 */
export const t = (key, args) => i18n.global.t(key, args)

export const currentLocale = () => i18n.global.locale.value

export const setLocale = code => {
  const next = normalize(code) || 'zh-CN'
  i18n.global.locale.value = next
  locale.value = next
  dayjs.locale(dayjsLocale(next))
  if (typeof document !== 'undefined') {
    document.documentElement.lang = next === 'zh-TW' ? 'zh-TW' : next === 'en-US' ? 'en' : 'zh-CN'
  }
  return next
}

export const localeLabel = code => locales.find(item => item.code === code)?.label || code

export default i18n
