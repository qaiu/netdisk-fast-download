/**
 * 下载器服务 - 统一管理 Aria2/Motrix/Gopeed/迅雷 的配置读取、连接检测、RPC 调用
 * 供 Home.vue、DirectoryTree.vue、DownloadDialog.vue 等共用
 */
import axios from 'axios'

const STORAGE_KEY = 'nfd-aria2-local-config'

const DEFAULT_CONFIG = {
  downloaderType: 'aria2',
  rpcUrl: 'http://localhost:6800/jsonrpc',
  rpcSecret: '',
  downloadDir: ''
}

/**
 * 从 localStorage 读取下载器配置
 * @returns {{ downloaderType: string, rpcUrl: string, rpcSecret: string, downloadDir: string }}
 */
export function getConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        downloaderType: parsed.downloaderType || DEFAULT_CONFIG.downloaderType,
        rpcUrl: parsed.rpcUrl || DEFAULT_CONFIG.rpcUrl,
        rpcSecret: parsed.rpcSecret || '',
        downloadDir: parsed.downloadDir || ''
      }
    }
  } catch (e) {
    console.warn('读取下载器配置失败', e)
  }
  return { ...DEFAULT_CONFIG }
}

/**
 * 保存下载器配置到 localStorage
 * @param {{ downloaderType?: string, rpcUrl?: string, rpcSecret?: string, downloadDir?: string }} config
 */
export function saveConfig(config) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
}

/**
 * 构建 RPC 参数数组（自动添加 token）
 * @param {string} rpcSecret
 * @param {Array} extraParams
 * @returns {Array}
 */
function buildRpcParams(rpcSecret, extraParams = []) {
  const params = []
  if (rpcSecret && rpcSecret.trim()) {
    params.push(`token:${rpcSecret}`)
  }
  if (extraParams && extraParams.length > 0) {
    params.push(...extraParams)
  }
  return params
}

/**
 * 调用 Aria2 JSON-RPC 接口
 * @param {string} rpcUrl
 * @param {string} rpcSecret
 * @param {string} method - 例如 'aria2.getVersion', 'aria2.addUri'
 * @param {Array} [extraParams] - 除 token 外的参数
 * @param {number} [timeout=5000]
 * @returns {Promise<Object>} RPC 响应的 data
 */
export async function callRpc(rpcUrl, rpcSecret, method, extraParams = [], timeout = 5000) {
  const requestBody = {
    jsonrpc: '2.0',
    id: Date.now().toString(),
    method,
    params: buildRpcParams(rpcSecret, extraParams)
  }
  const response = await axios.post(rpcUrl, requestBody, {
    headers: { 'Content-Type': 'application/json' },
    timeout
  })
  if (response.data && response.data.error) {
    throw new Error(response.data.error.message || 'Aria2 RPC 错误')
  }
  return response.data
}

/**
 * 判断 rpcUrl 是否指向 Gopeed（端口 9999 或 URL 含 /api/v1）
 * @param {string} url
 * @returns {boolean}
 */
function isGopeedUrl(url) {
  if (!url) return false
  return url.includes(':9999') || url.includes('/api/v1')
}

/**
 * 从 Gopeed rpcUrl 中提取 baseUrl（去掉 /jsonrpc 或 /api/v1 后缀）
 * 例如 "http://localhost:9999/jsonrpc" → "http://localhost:9999"
 * @param {string} rpcUrl
 * @returns {string}
 */
function gopeedBaseUrl(rpcUrl) {
  return rpcUrl.replace(/\/jsonrpc$/, '').replace(/\/api\/v1.*$/, '')
}

/**
 * 调用 Gopeed REST API
 * @param {string} baseUrl - 例如 "http://localhost:9999"
 * @param {string} rpcSecret - Bearer token
 * @param {string} method - 'GET' | 'POST'
 * @param {string} path - 例如 '/api/v1/version'
 * @param {Object} [body] - POST body
 * @param {number} [timeout=5000]
 * @returns {Promise<Object>} 响应 data
 */
async function callGopeedApi(baseUrl, rpcSecret, method, path, body, timeout = 5000) {
  const headers = { 'Content-Type': 'application/json' }
  if (rpcSecret && rpcSecret.trim()) {
    headers['X-Api-Token'] = rpcSecret
  }
  const url = baseUrl.replace(/\/$/, '') + path
  const response = await axios({ method, url, headers, data: body, timeout })
  return response.data
}

/**
 * 测试下载器连接（自动识别 迅雷 / Gopeed / Aria2 / Motrix）
 * @param {string} [rpcUrl] - 不传则自动读取配置
 * @param {string} [rpcSecret] - 不传则自动读取配置
 * @returns {Promise<{ connected: boolean, version: string }>}
 */
export async function testConnection(rpcUrl, rpcSecret) {
  if (!rpcUrl) {
    const config = getConfig()
    // 迅雷不需要 RPC，直接检测 JS SDK
    if (config.downloaderType === 'thunder') {
      const available = typeof window !== 'undefined' && window.thunderLink && typeof window.thunderLink.newTask === 'function'
      return { connected: available, version: available ? 'JS-SDK' : '' }
    }
    rpcUrl = config.rpcUrl
    rpcSecret = rpcSecret ?? config.rpcSecret
  }
  try {
    if (isGopeedUrl(rpcUrl)) {
      // Gopeed 使用 REST API：GET /api/v1/info
      const base = gopeedBaseUrl(rpcUrl)
      const res = await callGopeedApi(base, rpcSecret || '', 'GET', '/api/v1/info', undefined, 3000)
      const d = (res && res.code === 0 && res.data) ? res.data : {}
      const version = [d.version, d.runtime].filter(Boolean).join(' + ') || ''
      return { connected: true, version }
    } else {
      // Aria2 / Motrix 使用 JSON-RPC
      const res = await callRpc(rpcUrl, rpcSecret || '', 'aria2.getVersion', [], 3000)
      if (res && res.result && res.result.version) {
        return { connected: true, version: res.result.version }
      }
      return { connected: false, version: '' }
    }
  } catch {
    return { connected: false, version: '' }
  }
}

/**
 * 自动检测本地下载器（依次尝试 Motrix/Gopeed/Aria2）
 * @param {string} [rpcSecret] - 可选密钥
 * @returns {Promise<{ found: boolean, type: string, rpcUrl: string, version: string }>}
 */
export async function autoDetect(rpcSecret = '') {
  const candidates = [
    { type: 'motrix', port: 16800, path: '/jsonrpc' },
    { type: 'gopeed', port: 9999, path: '/api/v1/info', gopeed: true },
    { type: 'aria2', port: 6800, path: '/jsonrpc' }
  ]
  for (const c of candidates) {
    try {
      if (c.gopeed) {
        // Gopeed：直接调 REST GET /api/v1/info
        const base = `http://localhost:${c.port}`
        const res = await callGopeedApi(base, rpcSecret || '', 'GET', '/api/v1/info', undefined, 3000)
        const d = (res && res.code === 0 && res.data) ? res.data : {}
        const version = [d.version, d.runtime].filter(Boolean).join(' + ') || 'unknown'
        return { found: true, type: c.type, rpcUrl: `${base}/api/v1`, version }
      } else {
        const url = `http://localhost:${c.port}${c.path}`
        const result = await testConnection(url, rpcSecret)
        if (result.connected) {
          return { found: true, type: c.type, rpcUrl: url, version: result.version }
        }
      }
    } catch {
      // 该端口未响应，继续下一个
    }
  }
  return { found: false, type: '', rpcUrl: '', version: '' }
}

/**
 * 发送下载任务到下载器（自动识别 迅雷 / Gopeed / Aria2 / Motrix）
 * @param {string} downloadUrl - 文件下载地址
 * @param {Object} [headers] - 请求头 {cookie, referer, user-agent, ...}
 * @param {string} [fileName] - 输出文件名
 * @param {{ rpcUrl?: string, rpcSecret?: string, downloadDir?: string, downloaderType?: string }} [configOverride] - 覆盖配置
 * @returns {Promise<string>} 任务 ID / GID
 */
export async function addDownload(downloadUrl, headers, fileName, configOverride) {
  const config = { ...getConfig(), ...configOverride }

  if (config.downloaderType === 'thunder') {
    return addThunderDownload([{ url: downloadUrl, headers, fileName }], config)
  }

  if (isGopeedUrl(config.rpcUrl)) {
    // Gopeed REST API：POST /api/v1/tasks
    const base = gopeedBaseUrl(config.rpcUrl)
    const extraHeader = {}
    if (headers && typeof headers === 'object') {
      for (const [key, value] of Object.entries(headers)) {
        if (key && value) extraHeader[key] = value
      }
    }
    const body = {
      req: { url: downloadUrl, extra: { header: extraHeader } },
      opt: {}
    }
    if (config.downloadDir) body.opt.path = config.downloadDir
    const res = await callGopeedApi(base, config.rpcSecret, 'POST', '/api/v1/tasks', body, 10000)
    // Gopeed 返回 { code: 0, data: "task-id" }
    if (res && res.code !== undefined && res.code !== 0) throw new Error(res.message || 'Gopeed 发送失败')
    if (res && res.data) return typeof res.data === 'string' ? res.data : JSON.stringify(res.data)
    return 'ok'
  }

  // Aria2 / Motrix JSON-RPC
  const options = {}
  if (headers && typeof headers === 'object') {
    const headerArray = []
    for (const [key, value] of Object.entries(headers)) {
      if (key && value) headerArray.push(`${key}: ${value}`)
    }
    if (headerArray.length > 0) options.header = headerArray
  }
  if (fileName) options.out = fileName
  if (config.downloadDir) options.dir = config.downloadDir

  const res = await callRpc(config.rpcUrl, config.rpcSecret, 'aria2.addUri', [[downloadUrl], options], 10000)
  if (res && res.result) return res.result // GID
  throw new Error('未知错误')
}

/**
 * 批量发送下载任务到下载器（aria2 用 system.multicall，gopeed 用 batch API，迅雷用 JS-SDK newTask）
 * @param {{ url: string, headers?: Object, fileName?: string }[]} tasks - 下载任务列表
 * @param {{ rpcUrl?: string, rpcSecret?: string, downloadDir?: string, downloaderType?: string }} [configOverride]
 * @returns {Promise<{ succeeded: number, failed: number, errors: string[] }>}
 */
export async function batchAddDownload(tasks, configOverride) {
  if (!tasks || tasks.length === 0) return { succeeded: 0, failed: 0, errors: [] }
  if (tasks.length === 1) {
    try {
      await addDownload(tasks[0].url, tasks[0].headers, tasks[0].fileName, configOverride)
      return { succeeded: 1, failed: 0, errors: [] }
    } catch (e) {
      return { succeeded: 0, failed: 1, errors: [e.message || '未知错误'] }
    }
  }

  const config = { ...getConfig(), ...configOverride }

  if (config.downloaderType === 'thunder') {
    try {
      await addThunderDownload(tasks, config)
      return { succeeded: tasks.length, failed: 0, errors: [] }
    } catch (e) {
      return { succeeded: 0, failed: tasks.length, errors: [e.message || '迅雷下载失败'] }
    }
  }

  if (isGopeedUrl(config.rpcUrl)) {
    return batchAddGopeed(tasks, config)
  } else {
    return batchAddAria2(tasks, config)
  }
}

async function batchAddAria2(tasks, config) {
  const calls = tasks.map(task => {
    const options = {}
    if (task.headers && typeof task.headers === 'object') {
      const headerArray = []
      for (const [key, value] of Object.entries(task.headers)) {
        if (key && value) headerArray.push(`${key}: ${value}`)
      }
      if (headerArray.length > 0) options.header = headerArray
    }
    if (task.fileName) options.out = task.fileName
    if (config.downloadDir) options.dir = config.downloadDir

    const params = []
    if (config.rpcSecret && config.rpcSecret.trim()) {
      params.push(`token:${config.rpcSecret}`)
    }
    params.push([task.url], options)
    return { methodName: 'aria2.addUri', params }
  })

  try {
    const requestBody = {
      jsonrpc: '2.0',
      id: Date.now().toString(),
      method: 'system.multicall',
      params: [calls]
    }
    const response = await axios.post(config.rpcUrl, requestBody, {
      headers: { 'Content-Type': 'application/json' },
      timeout: Math.max(10000, tasks.length * 500)
    })
    const results = response.data && response.data.result
    if (!Array.isArray(results)) {
      throw new Error(response.data?.error?.message || 'system.multicall 返回异常')
    }
    let succeeded = 0, failed = 0
    const errors = []
    for (let i = 0; i < results.length; i++) {
      const r = results[i]
      if (Array.isArray(r) && r.length > 0 && typeof r[0] === 'string') {
        succeeded++
      } else if (r && r.faultCode) {
        failed++
        errors.push(`${tasks[i].fileName || tasks[i].url}: ${r.faultString || '未知错误'}`)
      } else {
        succeeded++
      }
    }
    return { succeeded, failed, errors }
  } catch (e) {
    return { succeeded: 0, failed: tasks.length, errors: [e.message || 'multicall 请求失败'] }
  }
}

async function batchAddGopeed(tasks, config) {
  const base = gopeedBaseUrl(config.rpcUrl)
  const reqs = tasks.map(task => {
    const extraHeader = {}
    if (task.headers && typeof task.headers === 'object') {
      for (const [key, value] of Object.entries(task.headers)) {
        if (key && value) extraHeader[key] = value
      }
    }
    const item = { req: { url: task.url, extra: { header: extraHeader } } }
    if (task.fileName) {
      item.opts = { name: task.fileName }
    }
    return item
  })

  const body = { reqs }
  if (config.downloadDir) body.opts = { path: config.downloadDir }

  try {
    const res = await callGopeedApi(base, config.rpcSecret, 'POST', '/api/v1/tasks/batch', body,
      Math.max(10000, tasks.length * 500))
    if (res && res.code !== undefined && res.code !== 0) {
      return { succeeded: 0, failed: tasks.length, errors: [res.message || 'Gopeed batch 失败'] }
    }
    const ids = Array.isArray(res?.data) ? res.data : []
    return { succeeded: ids.length || tasks.length, failed: 0, errors: [] }
  } catch (e) {
    return { succeeded: 0, failed: tasks.length, errors: [e.message || 'Gopeed batch 请求失败'] }
  }
}

/**
 * 通过迅雷 JS-SDK 发送下载任务
 * @param {{ url: string, headers?: Object, fileName?: string }[]} tasks
 * @param {{ downloadDir?: string }} config
 * @returns {Promise<string>}
 */
function addThunderDownload(tasks, config) {
  if (typeof window === 'undefined' || !window.thunderLink || typeof window.thunderLink.newTask !== 'function') {
    return Promise.reject(new Error('迅雷客户端未检测到，请确认已安装并启动迅雷'))
  }
  // 迅雷 JS-SDK 不支持自定义 Cookie，含 Cookie 的下载链接无法通过迅雷下载
  const firstHeaders = (tasks[0] && tasks[0].headers) || {}
  if (firstHeaders.cookie || firstHeaders.Cookie) {
    return Promise.reject(new Error('该文件需要 Cookie 认证，迅雷不支持自定义 Cookie，请使用 Aria2/Motrix/Gopeed'))
  }

  // 遍历所有 header key 大小写不敏感地提取 referer / user-agent
  let referer = ''
  let userAgent = ''
  for (const [key, value] of Object.entries(firstHeaders)) {
    const lk = key.toLowerCase()
    if (lk === 'referer' && value) referer = value
    if (lk === 'user-agent' && value) userAgent = value
  }

  const taskParam = {
    tasks: tasks.map(t => {
      const item = { url: t.url }
      if (t.fileName) item.name = t.fileName
      return item
    })
  }
  if (config.downloadDir) taskParam.downloadDir = config.downloadDir
  if (referer) taskParam.referer = referer
  if (userAgent) taskParam.userAgent = userAgent
  taskParam.threadCount = '1'

  console.log('[Thunder SDK] newTask params:', JSON.stringify(taskParam))
  window.thunderLink.newTask(taskParam)
  return Promise.resolve('thunder-ok')
}

/**
 * 根据 RPC URL 猜测下载器类型
 * @param {string} url
 * @returns {string}
 */
export function guessDownloaderType(url) {
  if (!url) return 'aria2'
  if (url.includes(':16800')) return 'motrix'
  if (url.includes(':9999')) return 'gopeed'
  return 'aria2'
}

/**
 * 检查下载头中是否含有 Cookie（迅雷不支持）
 * @param {Object} [headers]
 * @returns {boolean}
 */
export function hasCookieHeader(headers) {
  if (!headers || typeof headers !== 'object') return false
  return !!(headers.cookie || headers.Cookie)
}

/**
 * 检查下载头中是否含有自定义 User-Agent（迅雷客户端可能不支持）
 * @param {Object} [headers]
 * @returns {boolean}
 */
export function hasCustomUaHeader(headers) {
  if (!headers || typeof headers !== 'object') return false
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === 'user-agent' && headers[key]) return true
  }
  return false
}

export default {
  getConfig,
  saveConfig,
  callRpc,
  testConnection,
  autoDetect,
  addDownload,
  batchAddDownload,
  guessDownloaderType,
  hasCookieHeader
}
