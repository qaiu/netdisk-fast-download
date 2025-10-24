import axios from 'axios'

// 创建 axios 实例
const api = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || 'http://localhost:6400',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 可以在这里添加认证token等
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('API请求错误:', error)
    
    if (error.response) {
      // 服务器返回错误状态码
      const message = error.response.data?.message || error.response.data?.error || '服务器错误'
      return Promise.reject(new Error(message))
    } else if (error.request) {
      // 网络错误
      return Promise.reject(new Error('网络连接失败，请检查网络设置'))
    } else {
      // 其他错误
      return Promise.reject(new Error(error.message || '请求失败'))
    }
  }
)

// 客户端链接 API
export const clientLinksApi = {
  /**
   * 获取所有客户端下载链接
   * @param {string} shareUrl - 分享链接
   * @param {string} password - 提取码（可选）
   * @returns {Promise} 客户端链接响应
   */
  async getClientLinks(shareUrl, password = '') {
    const params = new URLSearchParams()
    params.append('url', shareUrl)
    if (password) {
      params.append('pwd', password)
    }
    
    return await api.get(`/v2/clientLinks?${params.toString()}`)
  },

  /**
   * 获取指定类型的客户端下载链接
   * @param {string} shareUrl - 分享链接
   * @param {string} password - 提取码（可选）
   * @param {string} clientType - 客户端类型
   * @returns {Promise} 指定类型的客户端链接
   */
  async getClientLink(shareUrl, password = '', clientType) {
    const params = new URLSearchParams()
    params.append('url', shareUrl)
    if (password) {
      params.append('pwd', password)
    }
    params.append('clientType', clientType)
    
    return await api.get(`/v2/clientLink?${params.toString()}`)
  }
}

// 其他 API（如果需要的话）
export const parserApi = {
  /**
   * 解析分享链接
   * @param {string} shareUrl - 分享链接
   * @param {string} password - 提取码（可选）
   * @returns {Promise} 解析结果
   */
  async parseLink(shareUrl, password = '') {
    const params = new URLSearchParams()
    params.append('url', shareUrl)
    if (password) {
      params.append('pwd', password)
    }
    
    return await api.get(`/v2/linkInfo?${params.toString()}`)
  },

  /**
   * 获取文件列表
   * @param {string} shareUrl - 分享链接
   * @param {string} password - 提取码（可选）
   * @param {string} dirId - 目录ID（可选）
   * @param {string} uuid - UUID（可选）
   * @returns {Promise} 文件列表
   */
  async getFileList(shareUrl, password = '', dirId = '', uuid = '') {
    const params = new URLSearchParams()
    params.append('url', shareUrl)
    if (password) {
      params.append('pwd', password)
    }
    if (dirId) {
      params.append('dirId', dirId)
    }
    if (uuid) {
      params.append('uuid', uuid)
    }
    
    return await api.get(`/v2/getFileList?${params.toString()}`)
  }
}

export default api
