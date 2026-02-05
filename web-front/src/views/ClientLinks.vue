<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px;">
            <el-icon @click="goBack" style="cursor: pointer; font-size: 20px;"><Close /></el-icon>
            <span>客户端下载链接生成器</span>
        </div>
      </template>

      <!-- 分享信息显示 -->
      <div v-if="form.shareUrl" style="margin-bottom: 20px;">
        <el-descriptions title="分享信息" :column="2" border>
          <el-descriptions-item label="分享链接">
            <el-link :href="form.shareUrl" target="_blank" type="primary">
              {{ form.shareUrl }}
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item label="提取码" v-if="form.password">
            <el-tag type="info">{{ form.password }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 认证提示 -->
      <div v-if="result && result.authRequirement && result.authRequirement !== 'none'" style="margin-bottom: 20px;">
        <el-alert
          :title="result.authRequirement === 'required' ? '需要认证信息' : '可选认证信息'"
          :type="result.authRequirement === 'required' ? 'warning' : 'info'"
          show-icon
          :closable="false"
        >
          <template #default>
            <div style="font-size: 13px;">
              {{ result.authHint }}
              <el-link type="primary" @click="goToAuthConfig" style="margin-left: 8px;">
                <el-icon><Key /></el-icon>
                去配置认证信息
              </el-link>
            </div>
          </template>
        </el-alert>
      </div>

      <!-- 需要客户端提示 -->
      <div v-if="result && result.requiresClient" style="margin-bottom: 20px;">
        <el-alert
          title="此网盘需要使用下载工具"
          type="warning"
          show-icon
          :closable="false"
        >
          <template #default>
            <div style="font-size: 13px;">
              该网盘的直链需要特殊请求头，浏览器无法直接下载。请使用以下客户端工具复制命令或链接进行下载。
            </div>
          </template>
        </el-alert>
      </div>

      <!-- 结果展示区域 -->
      <div v-if="result">
        <!-- 文件基本信息 -->
        <div v-if="result.fileName || result.fileSize" style="margin-bottom: 20px;">
          <el-descriptions title="文件信息" :column="2" border>
            <el-descriptions-item label="文件名" v-if="result.fileName">
              <el-tag type="success">{{ result.fileName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="文件大小" v-if="result.fileSize">
              <el-tag type="info">{{ formatFileSize(result.fileSize) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="解析器" v-if="result.parserInfo">
              <el-tag type="warning">{{ result.parserInfo }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="直链" v-if="result.directLink && !result.requiresClient">
              <el-link :href="result.directLink" target="_blank" type="primary">
                点击下载
              </el-link>
            </el-descriptions-item>
            <el-descriptions-item label="直链" v-else-if="result.directLink && result.requiresClient">
              <el-tag type="danger">需要客户端工具下载</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 客户端链接 Tab 切换 -->
        <el-tabs v-model="activeTab" type="card">
          <el-tab-pane 
            v-for="(link, type) in result.clientLinks" 
            :key="type"
            :name="type"
          >
            <template #label>
              <div style="display: flex; align-items: center; gap: 8px;">
                <img 
                  v-if="getClientLogo(type)"
                  :src="getClientLogo(type)" 
                  :alt="getClientDisplayName(type)"
                  style="width: 20px; height: 20px;"
                  @error="handleImageError"
                />
                <el-icon v-else :size="20"><Download /></el-icon>
                <span>{{ getClientDisplayName(type) }}</span>
                <el-tag v-if="!getClientSupportsCookie(type)" type="danger" size="small">不支持Cookie</el-tag>
              </div>
            </template>
            
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
                  <div>
                    <el-tag :type="getClientTagType(type)" size="large">
                      {{ getClientDisplayName(type) }}
                    </el-tag>
                    <span style="margin-left: 12px; color: #666;">
                      {{ getClientDescription(type) }}
                    </span>
                  </div>
                  <div style="display: flex; gap: 8px;">
                    <el-button
                      size="small"
                      type="primary"
                      @click="copyToClipboard(link)"
                      :icon="CopyDocument"
                    >
                      复制
                    </el-button>
                  </div>
                </div>
              </template>
              
              <el-input
                :model-value="link"
                type="textarea"
                :rows="getTextareaRows(link)"
                readonly
                placeholder="客户端下载命令将显示在这里"
                style="font-family: 'Courier New', 'Consolas', monospace; font-size: 13px;"
              />
            </el-card>
          </el-tab-pane>
        </el-tabs>

        <!-- 客户端说明 -->
        <div style="margin-top: 20px;">
          <el-divider content-position="left">
            <el-icon><InfoFilled /></el-icon>
            使用说明
          </el-divider>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="cURL 命令">
              支持Cookie，复制命令到终端运行即可下载
            </el-descriptions-item>
            <el-descriptions-item label="Aria2">
              支持Cookie，多线程下载器，复制命令后在终端运行或配置到Aria2客户端
            </el-descriptions-item>
            <el-descriptions-item label="迅雷">
              <el-text type="danger">不支持Cookie</el-text>，仅适用于无需Cookie的直链，点击下载按钮可唤起迅雷
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 错误信息 -->
      <div v-if="error" style="margin-top: 20px;">
        <el-alert
          :title="error"
          type="error"
          show-icon
          :closable="false"
        />
      </div>

      <!-- 空状态 -->
      <div v-if="!result && !error && !form.shareUrl" style="margin-top: 40px;">
        <el-empty description="请从主页点击'客户端下载'按钮开始使用" />
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Close, CopyDocument, Key, InfoFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

export default {
  name: 'ClientLinks',
  components: {
    Download,
    Close,
    CopyDocument,
    Key,
    InfoFilled
  },
  setup() {
    const form = reactive({
      shareUrl: '',
      password: ''
    })

    const result = ref(null)
    const error = ref('')
    const router = useRouter()
    const activeTab = ref('')

    // 从 sessionStorage 获取从 Home 页面传递的数据
    const loadDataFromHome = () => {
      try {
        const clientLinksData = sessionStorage.getItem('clientLinksData')
        const clientLinksForm = sessionStorage.getItem('clientLinksForm')
        
        if (clientLinksData) {
          const data = JSON.parse(clientLinksData)
          result.value = data
          sessionStorage.removeItem('clientLinksData')
        }
        
        if (clientLinksForm) {
          const formData = JSON.parse(clientLinksForm)
          form.shareUrl = formData.shareUrl
          form.password = formData.password
          sessionStorage.removeItem('clientLinksForm')
        }
      } catch (err) {
        console.error('加载数据失败:', err)
        error.value = '加载数据失败'
      }
    }

    // 客户端配置（只保留3种）
    const clientConfig = {
      'CURL': {
        displayName: 'cURL 命令',
        description: '命令行下载工具，支持Cookie',
        logo: 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/curl.svg',
        tagType: 'success',
        supportsCookie: true,
        downloadUrl: 'https://curl.se/download.html'
      },
      'ARIA2': {
        displayName: 'Aria2',
        description: '多线程下载器，支持Cookie',
        logo: 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/gnometerminal.svg',
        tagType: 'warning',
        supportsCookie: true,
        downloadUrl: 'https://aria2.github.io/'
      },
      'THUNDER': {
        displayName: '迅雷',
        description: '迅雷下载器，不支持Cookie',
        logo: null, // 使用base64
        tagType: 'primary',
        supportsCookie: false,
        downloadUrl: 'https://www.xunlei.com/'
      }
    }

    // 迅雷logo（base64）
    const thunderLogo = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBmaWxsPSIjMUJBMERCIiBkPSJNMTMgMkw0IDEzaDZsLTEgOWw5LTExaC02eiIvPjwvc3ZnPg=='

    // 获取客户端 Logo
    const getClientLogo = (type) => {
      if (type === 'THUNDER') {
        return thunderLogo
      }
      return clientConfig[type]?.logo || null
    }

    // 获取客户端显示名称
    const getClientDisplayName = (type) => {
      return clientConfig[type]?.displayName || type
    }

    // 获取客户端描述
    const getClientDescription = (type) => {
      return clientConfig[type]?.description || '下载工具'
    }

    // 获取客户端标签类型
    const getClientTagType = (type) => {
      return clientConfig[type]?.tagType || 'info'
    }

    // 获取客户端是否支持Cookie
    const getClientSupportsCookie = (type) => {
      return clientConfig[type]?.supportsCookie !== false
    }

    // 获取客户端下载链接
    const getClientDownloadUrl = (type) => {
      return clientConfig[type]?.downloadUrl || '#'
    }

    // 判断是否应该显示下载客户端按钮
    const shouldShowDownloadButton = (type) => {
      const os = getOSInfo()
      switch (type) {
        case 'CURL':
          // cURL 在 Windows 上可能需要安装
          return os === 'windows'
        case 'ARIA2':
          // Aria2 需要手动安装
          return true
        case 'THUNDER':
          // 迅雷主要在 Windows 上使用
          return os === 'windows'
        default:
          return false
      }
    }

    // 获取操作系统信息
    const getOSInfo = () => {
      const userAgent = navigator.userAgent.toLowerCase()
      if (userAgent.includes('windows')) return 'windows'
      if (userAgent.includes('mac')) return 'mac'
      if (userAgent.includes('linux')) return 'linux'
      return 'unknown'
    }

    // 处理图片加载错误
    const handleImageError = (event) => {
      event.target.style.display = 'none'
    }

    // 组件挂载时加载数据
    onMounted(() => {
      loadDataFromHome()
      
      // 设置默认激活的 tab
      if (result.value && result.value.clientLinks) {
        const firstClientType = Object.keys(result.value.clientLinks)[0]
        if (firstClientType) {
          activeTab.value = firstClientType
        }
      }
    })

    // 复制到剪贴板
    const copyToClipboard = async (text) => {
      try {
        await navigator.clipboard.writeText(text)
        ElMessage.success('已复制到剪贴板')
      } catch (err) {
        console.error('复制失败:', err)
        ElMessage.error('复制失败')
      }
    }

    // 使用客户端下载
    const downloadWithClient = (type, link) => {
      try {
        const os = getOSInfo()
        
        switch (type) {
          case 'CURL':
          case 'ARIA2':
            // 命令行工具，复制到剪贴板
            copyToClipboard(link)
            ElMessage.success(`${getClientDisplayName(type)} 命令已复制到剪贴板，请在终端中运行`)
            return
            
          case 'THUNDER':
            // 迅雷协议
            if (os !== 'windows') {
              ElMessage.warning('迅雷主要在 Windows 系统上使用，已复制链接到剪贴板')
              copyToClipboard(link)
              return
            }
            window.open(link, '_blank')
            ElMessage.success('正在唤起迅雷下载')
            break
            
          default:
            copyToClipboard(link)
            ElMessage.success('链接已复制到剪贴板')
        }
      } catch (err) {
        console.error('下载失败:', err)
        ElMessage.error('下载失败: ' + err.message)
      }
    }

    // 下载客户端
    const downloadClient = (type) => {
      const url = getClientDownloadUrl(type)
      window.open(url, '_blank')
      ElMessage.success(`正在跳转到 ${getClientDisplayName(type)} 下载页面`)
    }

    // 格式化文件大小
    const formatFileSize = (bytes) => {
      if (!bytes) return '未知'
      const units = ['B', 'KB', 'MB', 'GB', 'TB']
      let size = bytes
      let unitIndex = 0
      
      while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024
        unitIndex++
      }
      
      return `${size.toFixed(2)} ${units[unitIndex]}`
    }

    // 获取文本框行数
    const getTextareaRows = (text) => {
      if (!text) return 1
      const lines = text.split('\n').length
      return Math.min(Math.max(lines, 3), 10)
    }

    // 返回主页
    const goBack = () => {
      router.push('/')
    }

    // 跳转到认证配置
    const goToAuthConfig = () => {
      router.push('/')
      // 延迟提示用户点击认证按钮
      setTimeout(() => {
        ElMessage.info('请点击页面右上角的钥匙图标配置认证信息')
      }, 500)
    }

    return {
      form,
      result,
      error,
      activeTab,
      copyToClipboard,
      downloadWithClient,
      getClientTagType,
      getClientDisplayName,
      getClientDescription,
      formatFileSize,
      getTextareaRows,
      goBack,
      getClientLogo,
      downloadClient,
      handleImageError,
      shouldShowDownloadButton,
      getClientSupportsCookie,
      goToAuthConfig
    }
  }
}
</script>

<style scoped>
/* 使用 Element Plus 默认主题 */
</style>
