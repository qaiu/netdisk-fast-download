<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px;">
            <el-button @click="goBack" :icon="ArrowLeft" circle size="small" />
            <span>客户端下载链接生成器 (实验功能)</span>
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
            <el-descriptions-item label="直链" v-if="result.directLink">
              <el-link :href="result.directLink" target="_blank" type="primary">
                点击下载
              </el-link>
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
                <el-icon 
                  v-if="isElementIcon(getClientLogo(type))"
                  :size="20"
                  :color="getClientIconColor(type)"
                >
                  <component :is="getClientLogo(type)" />
                </el-icon>
                <img 
                  v-else
                  :src="getClientLogo(type)" 
                  :alt="getClientDisplayName(type)"
                  style="width: 20px; height: 20px;"
                  @error="handleImageError"
                />
                <span>{{ getClientDisplayName(type) }}</span>
                <el-icon 
                  v-if="isClientInstalled(type)" 
                  color="#67C23A" 
                  size="16"
                  title="已安装"
                >
                  <Check />
                </el-icon>
                <el-icon 
                  v-else 
                  color="#E6A23C" 
                  size="16"
                  title="未安装或无法检测"
                >
                  <QuestionFilled />
                </el-icon>
              </div>
            </template>
            
            <el-card>
                <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
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
                      <el-button
                        size="small"
                        type="success"
                        @click="downloadWithClient(type, link)"
                        :icon="Download"
                      >
                        下载
                      </el-button>
                    <el-button
                      v-if="!isClientInstalled(type) && shouldShowDownloadButton(type)"
                      size="small"
                      type="warning"
                      @click="downloadClient(type)"
                      :icon="Download"
                    >
                      下载客户端
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

        <!-- 支持的客户端类型 -->
        <div v-if="result.supportedClients" style="margin-top: 20px;">
          <el-divider content-position="left">
            <el-icon><Star /></el-icon>
            支持的客户端类型
          </el-divider>
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <el-tag
              v-for="(name, code) in result.supportedClients"
              :key="code"
              size="small"
              :type="getClientTagType(code.toUpperCase())"
            >
              {{ name }}
            </el-tag>
          </div>
          
          <!-- 添加说明信息 -->
          <el-alert
            title="客户端检测说明"
            type="info"
            :closable="false"
            style="margin-top: 12px;"
          >
            <template #default>
              <div style="font-size: 13px;">
                <p style="margin: 0 0 8px 0;">
                  • 为了避免自动打开外部应用，系统不会自动检测大部分下载器是否已安装
                </p>
                <p style="margin: 0;">
                  • 您可以点击"下载"按钮尝试使用相应的下载器，或点击"下载客户端"按钮安装
                </p>
              </div>
            </template>
          </el-alert>
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
        <el-empty description="请从主页点击'客户端链接(实验)'按钮开始使用" />
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, ArrowLeft, CopyDocument, Star, Check, QuestionFilled, Setting } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

export default {
  name: 'ClientLinks',
  components: {
    Download,
    ArrowLeft,
    CopyDocument,
    Star,
    Check,
    QuestionFilled,
    Setting
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
    const installedClients = ref(new Set())

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

    // 客户端 Logo 映射
    const getClientLogo = (type) => {
      const logoMap = {
        'ARIA2': 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/gnometerminal.svg',
        'MOTRIX': 'Download',
        'BITCOMET': 'Download',
        'THUNDER': 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB3aWR0aD0iNTYiIGhlaWdodD0iMjAiIHZpZXdCb3g9IjAgMCA1NiAyMCI+PGRlZnM+PHJlY3QgaWQ9ImEiIHdpZHRoPSI1NiIgaGVpZ2h0PSIyMCIgeD0iMCIgeT0iMCIgcng9IjMiLz48L2RlZnM+PGcgZmlsbD0ibm9uZSIgZmlsbC1ydWxlPSJldmVub2RkIj48bWFzayBpZD0iYiIgZmlsbD0iI2ZmZiI+PHVzZSB4bGluazpocmVmPSIjYSIvPjwvbWFzaz48dXNlIHhsaW5rOmhyZWY9IiNhIiBmaWxsPSIjMUJBMERCIi8+PHJlY3Qgd2lkdGg9IjIyIiBoZWlnaHQ9IjIwIiBmaWxsPSIjMEU2QUIyIiBtYXNrPSJ1cmwoI2IpIi8+PHRleHQgZmlsbD0iI0ZGRiIgZm9udC1mYW1pbHk9IlBpbmdGYW5nU0MtU2VtaWJvbGQsIFBpbmdGYW5nIFNDIiBmb250LXNpemU9IjEyIiBmb250LXdlaWdodD0iNTAwIiBtYXNrPSJ1cmwoI2IpIj48dHNwYW4geD0iMjYuNSIgeT0iMTQiPuq4gOq4gDwvdHNwYW4+PC90ZXh0PjxwYXRoIGZpbGw9IiNGRkYiIGQ9Ik0xNi44MTIxNDE4LDMuMTI4OCBDMTUuODU0OTIyLDMuNTMyNzU4MzMgMTQuNjEyNzA5Miw0LjE3MDM0MTY3IDEzLjU1NzI3NjYsNC44MDAwNSBDMTIuMjQ1NzMwNSw1LjU4MjE4MzMzIDEwLjk4MDM2ODgsNi40NjEzMjUgOS45MDc2MzEyMSw3LjMzNjU1ODMzIEw5LjY5NTc3MzA1LDcuNTA4ODE2NjcgOS42NTcyNDgyMyw3LjQ2MTI3NSBDOS42MzYwODUxMSw3LjQzMzU2NjY3IDkuNTY4NjgwODUsNy4zNDI0NSA5LjUwODk5MjkxLDcuMjU3MzQxNjcgQzguOTYzOTE0ODksNi40ODUwNjY2NyA4LjE5NzM5MDA3LDUuOTUwNDQxNjcgNy40NTU5NDMyNiw1LjgyMTc1ODMzIEM3LjEzNjIyNjk1LDUuNzY4MjY2NjcgNi42NzM5ODU4Miw1LjgxNTgwODMzIDYuMzM1MDM1NDYsNS45NDA1ODMzMyBDNi4xNzMyNzY2LDUuOTk3OTgzMzMgNi4xNTc4NDM5Nyw1Ljk5OTk2NjY3IDQuNjI4NjUyNDgsNi4xMTg3OTE2NyBDMy43ODEyMTk4Niw2LjE4NjEwODMzIDMuMDY2NzIzNCw2LjIzOTU0MTY3IDMuMDQxNzAyMTMsNi4yMzk1NDE2NyBDMi45NDUzNjE3LDYuMjM5NTQxNjcgMy4wMzIwNTY3NCw2LjI2MzM0MTY3IDMuNDA5NTMxOTEsNi4zNDA1MTY2NyBDNC4zNjI4OTM2Miw2LjUzMjYwODMzIDQuODk2MzQwNDMsNi42NzEyMDgzMyA1LjM3MjA4NTExLDYuODQ5NDE2NjcgQzUuNjYwOTM2MTcsNi45NTYzNDE2NyA2LjE5NjM2ODc5LDcuMTg2MDU4MzMgNi4yMDc5NDMyNiw3LjIwNzgxNjY3IEM2LjIyOTEwNjM4LDcuMjQzNDU4MzMgNi4wOTA0Mzk3Miw4LjM1MDMzMzMzIDYuMDI0OTY0NTQsOC42NzUwNzUgQzYuMDAzNzQ0NjgsOC43NzgwMzMzMyA1Ljk3Njc5NDMzLDguOTEyNzI1IDUuOTYzMzQ3NTIsOC45NzIxMDgzMyBDNS45NTE3NzMwNSw5LjAzMTQ5MTY3IDUuOTQyMTI3NjYsOS4yNDUzNDE2NyA1Ljk0MDE5ODU4LDkuNDQ3MzUgQzUuOTQwMTk4NTgsOS43NTIyNTgzMyA1Ljk0NzkxNDg5LDkuODUxMjUgNS45ODQ1MTA2NCwxMC4wMzU0MDgzIEM2LjEyMzE3NzMsMTAuNzQ2MjU4MyA2LjQ2MjEyNzY2LDExLjM2MjA4MzMgNy4wMzk5NDMyNiwxMS45NTIxODMzIEM3LjM3ODg5MzYyLDEyLjI5ODY4MzMgNy43MTIwNTY3NCwxMi41NjAwNzUgOC41NDAyNTUzMiwxMy4xMjI0MDgzIEM5LjAxMDE1NjAzLDEzLjQ0MzE4MzMgOS4zMDg2NTI0OCwxMy42MjkzMjUgOS44NjcxNzczLDEzLjk1MjA4MzMgQzEwLjA4ODY4MDksMTQuMDc4NzgzMyAxMC4zMjc0ODk0LDE0LjIxOTM2NjcgMTAuMzk2ODIyNywxNC4yNjQ5MjUgQzEwLjkxODc1MTgsMTQuNjAxNTY2NyAxMS4zMzg2MDk5LDE0Ljk4MzcwODMzIDEyLjQ5OTk3MTYsMTYuMTc5NzE2NyBDMTMuMTIwMTEzNSwxNi44MjEyNjY3IDEzLjMzNzc1ODksMTcuMDMzMTMzMyAxMy4zMzAwNDI2LDE2Ljk5MzUyNSBDMTMuMzAzMDkyMiwxNi44MjkxNDE3IDEzLjEyMDExMzUsMTUuODk4NDkxNyAxMy4wNTQ2MzgzLDE1LjU5NTU2NjcgQzEyLjc4MTE2MzEsMTQuMzIyMzI1IDEyLjQ2NzIzNCwxMy4yMTM0NjY3IDEyLjIyNDU2NzQsMTIuNjY1MDE2NyBDMTIuMTcyNTM5LDEyLjU0NDIwODMgMTIuMTE2NzA5MiwxMi40MTk0OTE3IDEyLjEwMzIwNTcsMTIuMzg3ODE2NyBMMTIuMDc4MTg0NCwxMi4zMjgzNzUgTDEyLjU5MDQ2ODEsMTIuMzM2MzA4MyBDMTMuMDIxOTAwNywxMi4zNDIyNTgzIDEzLjg1Nzc1ODksMTIuMzEwNTgzMyAxMy44ODI3ODAxLDEyLjI4ODc2NjcgQzEzLjg4NDcwOTIsMTIuMjg0OCAxMy44NjE2MTcsMTIuMTk1NzI1IDEzLjgyNjk1MDQsMTIuMDkwNzgzMyBDMTMuNzkyMjgzNywxMS45ODU4NDE3IDEzLjc2OTEzNDgsMTEuODk2NzA4MyAxMy43NzI5OTI5LDExLjg5MDc1ODMgQzEzLjc3ODc4MDEsMTEuODg2NzkxNyAxMy45MTkzNzU5LDExLjk1MDIgMTQuMDg4ODUxMSwxMi4wMzMzMjUgTDE0LjM5NTA2MzgsMTIuMTgxODQxNyBMMTQuOTUzNTg4NywxMi4wMTk1IEMxNS4yNTk4MDE0LDExLjkzMDM2NjcgMTUuNTE5ODI5OCwxMS44NTMxMzMzIDE1LjUyOTQ3NTIsMTEuODQ3MjQxNyBDMTUuNTM3MTkxNSwxMS44NDMyNzUgMTUuNDg5MDIxMywxMS43Nzk4NjY3IDE1LjQyMTYxNywxMS43MDY2NTgzIEMxNS4zNTQyMTI4LDExLjYzNTM3NSAxNS4yNjE3MzA1LDExLjUzMjM1ODMgMTUuMjEzNjE3LDExLjQ4MDkwODMgQzE1LjExMzQ3NTIsMTEuMzcwMDE2NyAxNS4wODI2NjY3LDExLjM2MjA4MzMgMTUuNTU2NDI1NSwxMS41NjYwMTY3IEwxNS44ODk1ODg3LDExLjcwODU4MzMgTDE2LjI2OTA0OTYsMTEuNTY2MDE2NyBDMTYuNDc3MDQ5NiwxMS40ODY4NTgzIDE2LjY1NjExMzUsMTEuNDE3NTU4MyAxNi42NjU3NTg5LDExLjQxMTYwODMgQzE2LjY3NTQwNDMsMTEuNDA1NjU4MzMgMTYuNTk0NDk2NSwxMS4zMTQ1NDE3IDE2LjQ4Mjc4MDEsMTEuMjA3NjE2NyBMMTYuMjgyNDk2NSwxMS4wMTM2IEwxNi42MjMzNzU5LDExLjEzMDQ0MTcgTDE2Ljk2NjE4NDQsMTEuMjQ1MjQxNyBMMTcuODY1NjQ1NCwxMC45MTQ2MDgzIEMxOC4zNjI0OTY1LDEwLjczMjQzMzMgMTguODE1MDkyMiwxMC41NjYwNjY3IDE4Ljg3MjkwNzgsMTAuNTQ2MjkxNyBDMTguOTMwNjY2NywxMC41Mjg0NDE3IDE4Ljk4NDU2NzQsMTAuNTA0NyAxOC45OTQyMTI4LDEwLjQ5ODc1IEMxOS4wMTU0MzI2LDEwLjQ3Njk5MTcgMTguMDYwMTQxOCwxMC4wNDUzMjUgMTcuMjEwNzgwMSw5LjY5Mjg3NSBDMTYuMjMyNDUzOSw5LjI4NjkzMzMzIDE0LjgxMjk5MjksOC43NjQyMDgzMyAxNC4yMzEzNzU5LDguNTk1ODU4MzMgQzE0LjExMDA3MDksOC41NjIyIDE0LjAwMjIxMjgsOC41MjY1NTgzMyAxMy45OTQ0OTY1LDguNTE4NjgzMzMgQzEzLjk4Njc4MDEsOC41MDg3NjY2NyAxNC4xOTY3MDkyLDguMTg5OTc1IDE0LjQ2MjQ2ODEsNy44MDU4NSBDMTQuNzI2MzU0Niw3LjQyMTY2NjY3IDE0Ljk0NCw3LjEwNDg1ODMzIDE0Ljk0NCw3LjEwMjg3NSBDMTQuOTQ0LDcuMDk4OTA4MzMgMTQuODg0MjU1Myw3LjA4OTA1IDE0LjgwOTEzNDgsNy4wODMxIEMxNC43MzYsNy4wNzUxNjY2NyAxNC42NzQzMjYyLDcuMDYzMjY2NjcgMTQuNjc0MzI2Miw3LjA1NzMxNjY3IEMxNC42NzQzMjYyLDcuMDUxNDI1IDE0Ljk2MTMwNSw2LjYyMzcyNSAxNS4zMTE4Mjk4LDYuMTA4ODc1IEMxNS42NjIzNTQ2LDUuNTk0MDI1IDE1Ljk1MTI2MjQsNS4xNjYzMjUgMTUuOTUzMTkxNSw1LjE1NjQ2NjY3IEMxNS45NTMxOTE1LDUuMTQ4NTMzMzMgMTUuODkxNTE3Nyw1LjEzNDY1IDE1LjgxNDUyNDgsNS4xMjY3MTY2NyBDMTUuNzM3NDc1Miw1LjEyMDgyNSAxNS42NzAwNzA5LDUuMTEwOTA4MzMgMTUuNjY2MjEyOCw1LjEwNjk0MTY3IEMxNS42NTg0OTY1LDUuMDk5MDA4MzMgMTUuODE0NTI0OCw0Ljg3NTI0MTY3IDE2LjY3MTU0NjEsMy42NjczOTE2NyBDMTYuOTI5NjQ1NCwzLjMwNTAyNSAxNy4xMzk1NzQ1LDMuMDA0MDgzMzMgMTcuMTM1NzE2MywzLjAwMjEgQzE3LjEzMTg1ODIsMi45OTgxMzMzMyAxNi45ODc0MDQzLDMuMDU1NTMzMzMgMTYuODEyMTQxOCwzLjEyODggTDE2LjgxMjE0MTgsMy4xMjg4IFoiIG1hc2s9InVybCgjYikiLz48L2c+PC9zdmc+',
        'WGET': 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/gnu.svg',
        'CURL': 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/curl.svg',
        'IDM': 'https://gcore.jsdelivr.net/gh/simple-icons/simple-icons@develop/icons/internetexplorer.svg',
        'FDM': 'Download',
        'POWERSHELL': 'Setting'
      }
      return logoMap[type] || 'Download'
    }

    // 判断是否为 Element Plus 图标
    const isElementIcon = (logo) => {
      const elementIcons = ['Download', 'Setting']
      return elementIcons.includes(logo)
    }

    // 获取客户端图标颜色
    const getClientIconColor = (type) => {
      const colorMap = {
        'MOTRIX': '#409EFF',
        'BITCOMET': '#67C23A',
        'FDM': '#E6A23C',
        'POWERSHELL': '#F56C6C'
      }
      return colorMap[type] || '#666'
    }

    // 客户端下载链接映射
    const getClientDownloadUrl = (type) => {
      const downloadUrls = {
        'ARIA2': 'https://aria2.github.io/',
        'MOTRIX': 'https://motrix.app/',
        'BITCOMET': 'https://www.bitcomet.com/',
        'THUNDER': 'https://www.xunlei.com/',
        'WGET': 'https://www.gnu.org/software/wget/',
        'CURL': 'https://curl.se/download.html',
        'IDM': 'https://www.internetdownloadmanager.com/download.html',
        'FDM': 'https://www.freedownloadmanager.org/',
        'POWERSHELL': 'https://docs.microsoft.com/en-us/powershell/'
      }
      return downloadUrls[type] || '#'
    }

    // 获取操作系统信息
    const getOSInfo = () => {
      const userAgent = navigator.userAgent.toLowerCase()
      if (userAgent.includes('windows')) return 'windows'
      if (userAgent.includes('mac')) return 'mac'
      if (userAgent.includes('linux')) return 'linux'
      if (userAgent.includes('android')) return 'android'
      if (userAgent.includes('ios')) return 'ios'
      return 'unknown'
    }

    // 获取设备类型
    const getDeviceType = () => {
      const userAgent = navigator.userAgent.toLowerCase()
      if (/mobile|android|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(userAgent)) {
        return 'mobile'
      }
      return 'desktop'
    }

    // 检查是否为移动端
    const isMobile = () => {
      return getDeviceType() === 'mobile'
    }

    // 检测客户端是否已安装（改进版本，避免自动打开应用）
    const detectClient = async (type) => {
      try {
        const os = getOSInfo()
        const deviceType = getDeviceType()
        
        // 移动端不支持大部分下载器
        if (deviceType === 'mobile') {
          switch (type) {
            case 'CURL':
              // 移动端可能支持 curl（通过终端应用）
              return os === 'android' || os === 'ios'
            default:
              return false
          }
        }
        
        switch (type) {
          case 'IDM':
            // IDM 只在 Windows 桌面端可用
            if (os !== 'windows' || deviceType !== 'desktop') return false
            return await testProtocol('idm://')
            
          case 'THUNDER':
            // 迅雷主要在 Windows 桌面端使用
            if (os !== 'windows' || deviceType !== 'desktop') return false
            return await testProtocol('thunder://')
            
          case 'BITCOMET':
            // 比特彗星主要在 Windows 桌面端使用
            if (os !== 'windows' || deviceType !== 'desktop') return false
            return await testProtocol('bc://')
            
          case 'MOTRIX':
            // Motrix 跨平台桌面端 - 使用保守检测，避免自动打开
            if (deviceType !== 'desktop') return false
            // 对于 Motrix，我们假设用户可能已安装，但不进行实际检测
            // 这样可以避免自动打开应用的问题
            return false // 改为 false，让用户手动选择
            
          case 'FDM':
            // FDM 主要在 Windows 桌面端使用
            if (os !== 'windows' || deviceType !== 'desktop') return false
            return await testProtocol('fdm://')
            
          case 'WGET':
            // wget 在 Linux/Mac 桌面端通常已安装
            return (os === 'linux' || os === 'mac') && deviceType === 'desktop'
            
          case 'CURL':
            // curl 在现代桌面系统上通常已安装
            return deviceType === 'desktop'
            
          case 'ARIA2':
            // aria2 需要手动安装，无法检测
            return false
            
          case 'POWERSHELL':
            // PowerShell 在 Windows 10+ 桌面端通常可用
            return os === 'windows' && deviceType === 'desktop'
            
          default:
            return false
        }
      } catch (error) {
        console.warn(`检测客户端 ${type} 失败:`, error)
        return false
      }
    }

    // 测试协议是否可用（改进版本，避免自动打开应用）
    const testProtocol = (protocol) => {
      return new Promise((resolve) => {
        // 对于某些协议，我们使用更安全的方式检测
        // 而不是直接尝试打开应用
        try {
          // 创建一个临时的 a 标签来测试协议
          const link = document.createElement('a')
          link.href = protocol + 'test'
          link.style.display = 'none'
          
          // 检查协议是否被识别
          const isProtocolSupported = link.protocol === protocol.slice(0, -1) // 移除末尾的 ':'
          
          if (isProtocolSupported) {
            // 如果协议被识别，我们假设应用可能已安装
            // 但不实际尝试打开它
            resolve(true)
          } else {
            resolve(false)
          }
        } catch (error) {
          // 如果检测失败，返回 false
          resolve(false)
        }
      })
    }

    // 检测所有客户端（改进版本，避免自动打开应用）
    const detectAllClients = async () => {
      const clientTypes = ['ARIA2', 'MOTRIX', 'BITCOMET', 'THUNDER', 'WGET', 'CURL', 'IDM', 'FDM', 'POWERSHELL']
      
      // 为了避免自动打开应用，我们使用更保守的检测方式
      // 只检测那些不会触发外部应用启动的客户端
      const safeDetectionTypes = ['WGET', 'CURL', 'POWERSHELL']
      
      const detectionPromises = safeDetectionTypes.map(async (type) => {
        const isInstalled = await detectClient(type)
        if (isInstalled) {
          installedClients.value.add(type)
        }
      })
      
      await Promise.all(detectionPromises)
      
      // 对于其他客户端，我们假设它们未安装，让用户手动选择
      // 这样可以避免自动打开外部应用的问题
    }

    // 检查客户端是否已安装
    const isClientInstalled = (type) => {
      return installedClients.value.has(type)
    }

    // 判断是否应该显示下载按钮
    const shouldShowDownloadButton = (type) => {
      const os = getOSInfo()
      const deviceType = getDeviceType()
      
      // 移动端不显示下载按钮
      if (deviceType === 'mobile') {
        return false
      }
      
      switch (type) {
        case 'CURL':
        case 'WGET':
          // 命令行工具，在 Linux/Mac 上通常已安装
          return os === 'windows'
        case 'POWERSHELL':
          // PowerShell 在 Windows 10+ 上通常可用
          return os !== 'windows'
        case 'ARIA2':
          // Aria2 需要手动安装
          return true
        case 'IDM':
        case 'THUNDER':
        case 'BITCOMET':
        case 'FDM':
          // Windows 专用工具
          return os === 'windows'
        case 'MOTRIX':
          // 跨平台工具
          return true
        default:
          return false
      }
    }

    // 下载客户端
    const downloadClient = (type) => {
      const url = getClientDownloadUrl(type)
      window.open(url, '_blank')
      ElMessage.success(`正在跳转到 ${getClientDisplayName(type)} 下载页面`)
    }

    // 处理图片加载错误
    const handleImageError = (event) => {
      event.target.src = 'el-icon-download'
    }

    // 组件挂载时加载数据和检测客户端
    onMounted(async () => {
    loadDataFromHome()
      await detectAllClients()
      
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
        const deviceType = getDeviceType()
        
        // 移动端特殊处理
        if (deviceType === 'mobile') {
          ElMessage.warning('移动端不支持直接启动下载器，请复制命令到终端使用')
          return
        }
        
        let downloadUrl = link
        let successMessage = `正在使用 ${getClientDisplayName(type)} 下载`
        
        switch (type) {
          case 'ARIA2':
            // Aria2 需要特殊处理，尝试复制到剪贴板
            copyToClipboard(link)
            ElMessage.success('Aria2 命令已复制到剪贴板，请在终端中运行')
            return
            
          case 'CURL':
          case 'WGET':
          case 'POWERSHELL':
            // 命令行工具，复制到剪贴板
            copyToClipboard(link)
            ElMessage.success(`${getClientDisplayName(type)} 命令已复制到剪贴板，请在终端中运行`)
            return
            
          case 'THUNDER':
            // 迅雷协议
            if (os !== 'windows') {
              ElMessage.warning('迅雷仅在 Windows 系统上可用')
              return
            }
            window.open(link, '_blank')
            break
            
          case 'IDM':
            // IDM 协议
            if (os !== 'windows') {
              ElMessage.warning('IDM 仅在 Windows 系统上可用')
              return
            }
            window.open(link, '_blank')
            break
            
          case 'BITCOMET':
            // 比特彗星协议
            if (os !== 'windows') {
              ElMessage.warning('比特彗星仅在 Windows 系统上可用')
              return
            }
            window.open(link, '_blank')
            break
            
          case 'MOTRIX':
            // Motrix JSON 格式处理
            try {
              const jsonData = JSON.parse(link)
              if (jsonData.url) {
              downloadUrl = jsonData.url
              window.open(downloadUrl, '_blank')
              } else {
                ElMessage.warning('Motrix 链接格式错误')
                return
              }
            } catch {
              // 如果不是 JSON 格式，尝试直接打开
              window.open(link, '_blank')
            }
            break
            
          case 'FDM':
            // FDM 协议
            if (os !== 'windows') {
              ElMessage.warning('FDM 仅在 Windows 系统上可用')
              return
            }
            window.open(link, '_blank')
            break
            
          default:
            // 其他情况，直接下载文件
            window.open(downloadUrl, '_blank')
            successMessage = '正在下载文件'
        }
        
        ElMessage.success(successMessage)
      } catch (err) {
        console.error('下载失败:', err)
        ElMessage.error('下载失败: ' + err.message)
      }
    }

    // 获取客户端标签类型
    const getClientTagType = (type) => {
      const tagTypes = {
        'ARIA2': 'warning',
        'MOTRIX': 'info',
        'BITCOMET': 'success',
        'THUNDER': 'primary',
        'WGET': 'info',
        'CURL': 'success',
        'IDM': 'danger',
        'FDM': 'warning',
        'POWERSHELL': 'primary'
      }
      return tagTypes[type] || 'info'
    }

    // 获取客户端显示名称
    const getClientDisplayName = (type) => {
      const displayNames = {
        'ARIA2': 'Aria2',
        'MOTRIX': 'Motrix',
        'BITCOMET': '比特彗星',
        'THUNDER': '迅雷',
        'WGET': 'wget 命令',
        'CURL': 'cURL 命令',
        'IDM': 'IDM',
        'FDM': 'Free Download Manager',
        'POWERSHELL': 'PowerShell'
      }
      return displayNames[type] || type
    }

    // 获取客户端描述
    const getClientDescription = (type) => {
      const descriptions = {
        'ARIA2': '多线程下载器',
        'MOTRIX': '跨平台下载器',
        'BITCOMET': '比特彗星下载器',
        'THUNDER': '迅雷下载器',
        'WGET': 'Linux 下载工具',
        'CURL': '命令行下载工具',
        'IDM': 'Windows 下载管理器',
        'FDM': '免费下载管理器',
        'POWERSHELL': 'Windows PowerShell'
      }
      return descriptions[type] || '下载工具'
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
      isClientInstalled,
      downloadClient,
      handleImageError,
      shouldShowDownloadButton,
      isElementIcon,
      getClientIconColor
    }
  }
}
</script>

<style scoped>
/* 移除所有自定义样式，使用 Element Plus 默认主题 */
</style>