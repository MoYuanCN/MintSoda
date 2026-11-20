import zhCn from 'element-plus/es/locale/lang/zh-cn'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import en from 'element-plus/es/locale/lang/en'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/zh-tw'
import 'dayjs/locale/en'

const ELEMENT_LOCALES = {
  'zh-CN': zhCn,
  'zh-TW': zhTw,
  'en-US': en
}

const DAYJS_LOCALES = {
  'zh-CN': 'zh-cn',
  'zh-TW': 'zh-tw',
  'en-US': 'en'
}

export const elementLocale = code => ELEMENT_LOCALES[code] || zhCn

export const dayjsLocale = code => DAYJS_LOCALES[code] || 'zh-cn'
