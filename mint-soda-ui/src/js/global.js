import {useColorMode, useDark, useDebounceFn, useEventListener, useLocalStorage} from "@vueuse/core";
import {ref, watch} from "vue";

/**
 * 保存登录信息
 */
let rememberThePassword = useLocalStorage('rememberThePassword', {
    remember: false,
    username: '',
    password: ''
})

/**
 * 令牌
 */
const authorization = useLocalStorage('authorization', '')

const uiCustomization = ref({})
const uiAssetUrl = path => path ? toApiUrl('api/uiAsset', {filename: path}) : ''

const clamp = (value, min, max, fallback) => {
    const number = Number(value)
    return Number.isFinite(number) ? Math.max(min, Math.min(max, number)) : fallback
}

/**
 * 从背景图取主色，画笔很小，只为得到一个稳定的代表色。
 */
const pickColorFromImage = url => new Promise(resolve => {
    if (!url) {
        resolve('')
        return
    }
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => {
        try {
            const canvas = document.createElement('canvas')
            canvas.width = 12
            canvas.height = 12
            const context = canvas.getContext('2d', {willReadFrequently: true})
            context.drawImage(image, 0, 0, canvas.width, canvas.height)
            const {data} = context.getImageData(0, 0, canvas.width, canvas.height)
            let r = 0, g = 0, b = 0, count = 0
            for (let i = 0; i < data.length; i += 4) {
                const alpha = data[i + 3]
                if (alpha < 200) continue
                r += data[i]
                g += data[i + 1]
                b += data[i + 2]
                count++
            }
            resolve(count ? toAccentColor(r / count, g / count, b / count) : '')
        } catch {
            resolve('')
        }
    }
    image.onerror = () => resolve('')
    image.src = url
})

/**
 * 背景色往往过亮或过灰，先提饱和再限制亮度，保证作为强调色可读。
 */
const toAccentColor = (r, g, b) => {
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    let hue = 0
    if (max !== min) {
        if (max === r) hue = ((g - b) / (max - min)) % 6
        else if (max === g) hue = (b - r) / (max - min) + 2
        else hue = (r - g) / (max - min) + 4
        hue = (hue * 60 + 360) % 360
    }
    const saturation = Math.min(72, Math.max(52, max === 0 ? 0 : (max - min) / max * 130))
    return hslToHex(hue, saturation, 52)
}

const hslToHex = (h, s, l) => {
    const a = s / 100 * Math.min(l / 100, 1 - l / 100)
    const channel = n => {
        const k = (n + h / 30) % 12
        const color = l / 100 - a * Math.max(-1, Math.min(k - 3, Math.min(9 - k, 1)))
        return Math.round(255 * color).toString(16).padStart(2, '0')
    }
    return `#${channel(0)}${channel(8)}${channel(4)}`
}

/**
 * 是否处于深色模式。以 html.dark 为准，才能覆盖"跟随系统"的情况。
 */
const isDarkMode = () => document.documentElement.classList.contains('dark')

const resolveBackground = (light, dark, isDark) => {
    const preferred = isDark ? (dark || light) : light
    return preferred || ''
}

const applyThemeColor = async config => {
    const accent = String(config.accentColor || '').trim()
    if (accent) {
        colorChange(accent)
        return
    }
    if (config.themeDynamicColor) {
        const isDark = isDarkMode()
        const source = resolveBackground(config.appBackgroundImage, config.appBackgroundImageDark, isDark)
            || resolveBackground(config.loginBackgroundImage, config.loginBackgroundImageDark, isDark)
        const picked = await pickColorFromImage(source ? uiAssetUrl(source) : '')
        colorChange(picked || '#409eff')
        return
    }
    colorChange(color.value || '#409eff')
}

/**
 * 按当前亮暗模式选择背景图，并让背景层单独拥有模糊度与不透明度。
 */
const applyBackground = config => {
    const root = document.documentElement
    const isDark = isDarkMode()
    const appImage = resolveBackground(config.appBackgroundImage, config.appBackgroundImageDark, isDark)
    const loginImage = resolveBackground(config.loginBackgroundImage, config.loginBackgroundImageDark, isDark)
    root.style.setProperty('--app-background-image', appImage ? `url("${uiAssetUrl(appImage)}")` : 'none')
    root.style.setProperty('--login-background-image', loginImage ? `url("${uiAssetUrl(loginImage)}")` : 'none')
    root.style.setProperty('--app-background-blur', `${clamp(config.backgroundBlur, 0, 40, 10)}px`)
    root.style.setProperty('--app-background-opacity', `${clamp(config.backgroundOpacity, 0, 100, 100)}%`)
}

const applyUiCustomization = config => {
    uiCustomization.value = {...config}
    const root = document.documentElement
    root.style.setProperty('--app-card-opacity', `${clamp(config.cardOpacity, 0, 100, 80)}%`)
    root.style.setProperty('--app-card-blur', `${clamp(config.cardBlur, 0, 40, 14)}px`)
    root.style.setProperty('--app-corner-radius', `${clamp(config.appCornerRadius, 0, 40, 12)}px`)
    root.style.setProperty('--app-nav-opacity', `${clamp(config.navOpacity, 0, 100, 78)}%`)
    root.style.setProperty('--app-nav-blur', `${clamp(config.navBlur, 0, 40, 18)}px`)
    root.style.setProperty('--app-dialog-opacity', `${clamp(config.dialogOpacity, 0, 100, 84)}%`)
    root.style.setProperty('--app-dialog-blur', `${clamp(config.dialogBlur, 0, 40, 16)}px`)
    applyBackground(config)
    applyThemeColor(config)
}

/**
 * 主题管理
 */
const {store} = useColorMode()

// 亮暗模式切换后背景图与动态取色需要重新计算。
const syncThemeBackground = () => {
    const config = uiCustomization.value
    if (!config || Object.keys(config).length === 0) return
    applyBackground(config)
    applyThemeColor(config)
}

// 跟随系统时 store 不会变化，需要同时监听 html.dark 的增删。
watch(store, syncThemeBackground)
if (typeof MutationObserver !== 'undefined') {
    new MutationObserver(syncThemeBackground)
        .observe(document.documentElement, {attributes: true, attributeFilter: ['class']})
}

/**
 * 最大内容宽度
 */
const maxContentWidth = useLocalStorage('max-content-width', 3200);

/**
 * 显示评分
 */
const showScore = useLocalStorage('show-score', true)

/**
 * 按星期展示
 */
const showWeek = useLocalStorage("show-week", true)

/**
 * 点击订阅封面时执行的操作
 */
const coverClickAction = useLocalStorage('cover-click-action', 'edit')

/**
 * 订阅页排序方式
 */
const subscriptionSort = useLocalStorage('subscription-sort', 'SCORE')

/**
 * 启动页
 */
const startupPage = useLocalStorage('startup-page', '/home')

/**
 * 显示更新时间
 */
const showLastDownloadTime = useLocalStorage("show-last-download-time", true);

/**
 * 强调色
 */
const color = useLocalStorage('--el-color-primary', '#409eff')

/**
 * 改动强调色
 */
const colorChange = (v) => {
    // 同步到本地存储，未在设置中指定强调色时沿用上次选择。
    color.value = v
    const el = document.documentElement
    el.style.setProperty('--el-color-primary', v)
}

/**
 * 主题初始化
 */
const initTheme = () => {
    /**
     * 夜间模式
     */
    useDark({
        onChanged: dark => {
            // 自动根据夜间模式修改沉浸式状态栏
            const meta = document.getElementById('themeColorMeta');
            meta.content = dark ? '#000000' : '#ffffff';
        }
    })

    // 修改强调色
    colorChange(color.value)
}

/**
 * 布局初始化
 */
const initLayout = () => {
    let app = document.querySelector('#app');

    // 设置最大布局宽度
    maxContentWidth.value = Math.max(maxContentWidth.value, 1200)

    app
        .style.maxWidth = `${maxContentWidth.value}px`

    const el = document.documentElement
    el.style.setProperty('--max-content-width', `${maxContentWidth.value}px`)

}

/**
 * 初始化
 */
const init = () => {
    initTheme()
    initLayout()
}

/**
 * 当页面大小变化时重新计算一下布局
 * 对方法做节流处理
 */
useEventListener(window, 'resize', useDebounceFn(initLayout, 500))

const base64Encode = s => {
    const encoder = new TextEncoder();
    const data = encoder.encode(s);
    return window.btoa(String.fromCharCode(...data));
}

const getBaseUrl = () => {
    const {protocol, host, pathname} = location
    return `${protocol}//${host}${pathname}`
}

const toApiUrl = (path, params) => {
    const url = new URL(getBaseUrl())
    url.pathname += path
    url.search = new URLSearchParams(params).toString()
    return url.toString();
}

const proxyImage = imgUrl => {
    return toApiUrl('api/proxyImage', {
        imgUrl: base64Encode(imgUrl),
        s: authorization.value
    })
}

const toApiFile = filename => {
    return toApiUrl('api/file', {
        filename: base64Encode(filename),
        s: authorization.value
    })
}

export {
    rememberThePassword,
    authorization,
    store,
    maxContentWidth,
    showScore,
    showWeek,
    coverClickAction,
    startupPage,
    subscriptionSort,
    showLastDownloadTime,
    color,
    colorChange,
    init,
    initTheme,
    initLayout,
    base64Encode,
    toApiUrl,
    proxyImage,
    toApiFile,
    getBaseUrl,
    uiCustomization,
    uiAssetUrl,
    applyUiCustomization
};
