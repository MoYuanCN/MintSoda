/**
 * 插件相关接口
 */
import api from './api.js'

export const listPlugins = () => api.post('api/plugin/list')

export const pluginStats = () => api.post('api/plugin/stats')

export const pluginInfo = id => api.post('api/plugin/info', {id})

export const enablePlugin = id => api.post('api/plugin/enable', {id})

export const disablePlugin = id => api.post('api/plugin/disable', {id})

export const reloadPlugin = id => api.post('api/plugin/reload', {id})

export const uninstallPlugin = id => api.post('api/plugin/uninstall', {id})

export const savePluginConfig = (id, config) => api.post('api/plugin/saveConfig', {id, config})

export const installPlugin = (url, sha256, size) => api.post('api/plugin/install', {
    url,
    sha256: sha256 || '',
    size: size == null ? '' : String(size)
})

export const getPluginMarket = (url) => api.post('api/plugin/market', {url: url || ''})

export const installPluginFromMarket = entry => api.post('api/plugin/installFromMarket', entry)

export const checkPluginUpdate = (id, marketUrl) => api.post('api/plugin/checkUpdate', {
    id,
    marketUrl: marketUrl || ''
})

export const checkAllPluginUpdates = (marketUrl) => api.post('api/plugin/checkAll', {
    marketUrl: marketUrl || ''
})

export const updatePlugin = (id, marketUrl) => api.post('api/plugin/update', {
    id,
    marketUrl: marketUrl || ''
})

export const getPluginMarketUrl = () => api.post('api/plugin/marketUrl')

export const setPluginMarketUrl = url => api.post('api/plugin/setMarketUrl', {url})

export const getPluginPermissions = () => api.post('api/plugin/permissions')

/**
 * 调用插件自定义接口，路径为 /api/plugin/api/{id}/{path}。
 */
export const callPlugin = (id, path, body, method = 'POST', options = {}) => {
  const normalized = String(path || '').replace(/^\/+/, '')
  const url = `api/plugin/api/${encodeURIComponent(id)}/${normalized}`
  const name = String(method || 'POST').toUpperCase()
  if (name === 'GET') return api.get(url, options)
  if (name === 'PUT') return api.put(url, body || {})
  if (name === 'DELETE') return api.del(url, body || {})
  return api.post(url, body || {})
}

export const setPluginPermissions = (id, permissions) => api.post('api/plugin/setPermissions', {
    id,
    permissions: permissions || []
})
