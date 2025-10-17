<template>
  <div id="app" v-cloak  :class="{ 'dark-theme': isDarkMode }">
    <!-- <el-dialog
      v-model="showRiskDialog"
      title="使用本网站您应改同意"
      width="300px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      center
    >
      <div style="font-size:1.08em;line-height:1.8;">
        请勿在本平台分享、传播任何违法内容，包括但不限于：<br>
        违规视频、游戏外挂、侵权资源、涉政涉黄等。<br>        
      </div>
      <template #footer>
        <el-button type="primary" @click="ackRisk">我知道了</el-button>
      </template>
    </el-dialog> -->
    <!-- 顶部反馈栏（小号、灰色、无红边框） -->
    <div class="feedback-bar">
      <a href="https://github.com/qaiu/netdisk-fast-download/issues" target="_blank" rel="noopener" class="feedback-link mini">
        <i class="fas fa-bug feedback-icon"></i>
        反馈
      </a>
      <a href="https://github.com/qaiu/netdisk-fast-download" target="_blank" rel="noopener" class="feedback-link mini">
        <i class="fab fa-github feedback-icon"></i>
        源码
      </a>
      <a href="https://blog.qaiu.top" target="_blank" rel="noopener" class="feedback-link mini">
        <i class="fas fa-blog feedback-icon"></i>
        博客
      </a>
      <a href="https://blog.qaiu.top/archives/netdisk-fast-download-bao-ta-an-zhuang-jiao-cheng" target="_blank" rel="noopener" class="feedback-link mini">
        <i class="fas fa-server feedback-icon"></i>
        部署
      </a>
    </div>
    <el-row :gutter="20" style="margin-left: 0; margin-right: 0;">
      <el-card class="box-card">
        <div style="text-align: right">
          <DarkMode @theme-change="handleThemeChange" />
        </div>
        <div class="demo-basic--circle">
          <div class="block" style="text-align: center;">
            <img :height="150" src="../../public/images/lanzou111.png" alt="lz">
          </div>
        </div>
        <!-- 项目简介移到卡片内 -->
        <div class="project-intro">
          <div class="intro-title">NFD网盘直链解析0.1.9_b10</div>
          <div class="intro-desc">
            <div>支持网盘：蓝奏云、蓝奏云优享、小飞机盘、123云盘、奶牛快传、移动云空间、QQ邮箱云盘、QQ闪传等 <el-link style="color:#606cf5" href="https://github.com/qaiu/netdisk-fast-download?tab=readme-ov-file#%E7%BD%91%E7%9B%98%E6%94%AF%E6%8C%81%E6%83%85%E5%86%B5" target="_blank"> &gt;&gt; </el-link></div>
            <div>文件夹解析支持：蓝奏云、蓝奏云优享、小飞机盘、123云盘</div>
          </div>
        </div>
        <div class="typo">
          <p>节点1: 回源请求数:{{ node1Info.parserTotal }}, 缓存请求数:{{ node1Info.cacheTotal }}, 总数:{{ node1Info.total }}</p>
        </div>
        
        <hr>
        
        <div class="main" v-loading="isLoading">
          <div class="grid-content">
            <!-- 开关按钮，控制是否自动读取剪切板 -->
            <el-switch v-model="autoReadClipboard" active-text="自动识别剪切板"></el-switch>

            <el-input placeholder="请粘贴分享链接(http://或https://)" v-model="link" id="url">
              <template #prepend>分享链接</template>
              <template #append v-if="!autoReadClipboard">
                <el-button @click="getPaste(true)">读取剪切板</el-button>
              </template>
            </el-input>
            
            <el-input placeholder="请输入密码" v-model="password" id="url">
              <template #prepend>分享密码</template>
            </el-input>
            
            <el-input v-show="directLink" :value="directLink" id="url">
              <template #prepend>智能直链</template>
              <template #append>
                <el-button v-clipboard:copy="directLink" v-clipboard:success="onCopy" v-clipboard:error="onError">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </template>
            </el-input>

            <p style="text-align: center">
              <el-button style="margin-left: 40px" @click="parseFile">解析文件</el-button>
              <el-button style="margin-left: 20px" @click="parseDirectory">解析目录</el-button>
              <el-button style="margin-left: 20px" @click="generateMarkdown">生成Markdown</el-button>
              <el-button style="margin-left: 20px" @click="generateQRCode">扫码下载</el-button>
              <el-button style="margin-left: 20px" @click="getStatistics">分享统计</el-button>
            </p>
          </div>

          <!-- 解析结果 -->
          <div v-if="parseResult.code" style="margin-top: 10px">
            <strong>解析结果: </strong>
            <json-viewer :value="parseResult" :expand-depth="5" copyable boxed sort />
            <!-- 文件信息美化展示区 -->
            <div v-if="downloadUrl" class="file-meta-info-card">
              <div class="file-meta-row">
                <span class="file-meta-label">下载链接：</span>
                <a :href="downloadUrl" target="_blank" class="file-meta-link" rel="noreferrer noopener">点击下载</a>
              </div>
              <div class="file-meta-row" v-if="parseResult.data?.downloadShortUrl">
                <span class="file-meta-label">下载短链：</span>
                <a :href="parseResult.data.downloadShortUrl" target="_blank" class="file-meta-link">{{ parseResult.data.downloadShortUrl }}</a>
              </div>
              <div class="file-meta-row">
                <span class="file-meta-label">文件预览：</span>
                <a :href="previewBaseUrl + encodeURIComponent(downloadUrl)" target="_blank" class="file-meta-link">点击预览</a>
              </div>
              <div class="file-meta-row">
                <span class="file-meta-label">文件名：</span>{{ extractFileNameAndExt(downloadUrl).name }}
              </div>
              <div class="file-meta-row">
                <span class="file-meta-label">文件类型：</span>{{ getFileTypeClass({ fileName: extractFileNameAndExt(downloadUrl).name }) }}
              </div>
              <div class="file-meta-row" v-if="parseResult.data?.sizeStr">
                <span class="file-meta-label">文件大小：</span>{{ parseResult.data.sizeStr }}
              </div>
            </div>
          </div>

          <!-- Markdown链接 -->
          <div v-if="markdownText" style="text-align: center">
            <el-input :value="markdownText" readonly>
              <template #append>
                <el-button v-clipboard:copy="markdownText" v-clipboard:success="onCopy" v-clipboard:error="onError">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>

          <!-- 二维码 -->
          <div style="text-align: center" v-show="showQRCode">
            <canvas ref="qrcodeCanvas"></canvas>
            <div style="text-align: center">
              <el-link target="_blank" :href="qrCodeUrl">{{ qrCodeUrl }}</el-link>
            </div>
          </div>

          <!-- 统计信息 -->
          <div v-if="statisticsData.shareLinkInfo">
            <el-descriptions class="margin-top" title="分享详情" :column="1" border>
              <template slot="extra">
                <el-button type="primary" size="small">操作</el-button>
              </template>
              <el-descriptions-item label="网盘名称">{{ statisticsData.shareLinkInfo.panName }}</el-descriptions-item>
              <el-descriptions-item label="网盘标识">{{ statisticsData.shareLinkInfo.type }}</el-descriptions-item>
              <el-descriptions-item label="分享Key">{{ statisticsData.shareLinkInfo.shareKey }}</el-descriptions-item>
              <el-descriptions-item label="分享链接">
                <el-link target="_blank" :href="statisticsData.shareLinkInfo.shareUrl">{{ statisticsData.shareLinkInfo.shareUrl }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="jsonApi链接">
                <el-link target="_blank" :href="statisticsData.apiLink">{{ statisticsData.apiLink }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="302下载链接">
                <el-link target="_blank" :href="statisticsData.downLink">{{ statisticsData.downLink }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="302预览链接">
                <el-link target="_blank" :href="statisticsData.viewLink">{{ statisticsData.viewLink }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="解析次数">{{ statisticsData.parserTotal }}</el-descriptions-item>
              <el-descriptions-item label="缓存命中次数">{{ statisticsData.cacheHitTotal }}</el-descriptions-item>
              <el-descriptions-item label="总请求次数">{{ statisticsData.sumTotal }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 错误时显示小按钮 -->
          <div v-if="errorButtonVisible" style="text-align: center; margin-top: 10px;">
            <el-button type="text" @click="errorDialogVisible = true"> 反馈错误详情>> </el-button>
          </div>

          <!-- 错误 JSON 弹窗 -->
          <el-dialog
              v-model="errorDialogVisible"
              width="60%">
            <template #title>
              错误详情
              <el-link
                  @click.prevent="copyErrorDetails"
                  target="_blank"
                  style="margin-left:8px"
                  type="primary">
                复制当前错误信息，提交Issue
              </el-link>
            </template>
            <json-viewer :value="errorDetail" :expand-depth="5" copyable boxed sort />
            <template #footer>
              <el-button @click="errorDialogVisible = false">关闭</el-button>
            </template>
          </el-dialog>
          <!-- 目录树组件 -->
          <div v-if="showDirectoryTree" class="directory-tree-container">
            <div style="margin-bottom: 10px; text-align: right;">
              <el-radio-group v-model="directoryViewMode" size="small">
                <el-radio-button label="pane">窗格</el-radio-button>
                <el-radio-button label="tree">文件树</el-radio-button>
              </el-radio-group>
            </div>
            <DirectoryTree 
              :file-list="directoryData" 
              :share-url="link"
              :password="password"
              :view-mode="directoryViewMode"
              @file-click="handleFileClick"
            />
          </div>
        </div>
      </el-card>
    </el-row>
    
    <!-- 版本号显示 -->
    <div class="version-info">
      <span class="version-text">内部版本: {{ buildVersion }}</span>
    </div>
    
    <!-- 文件解析结果区下方加分享按钮 -->
<!--    <div v-if="parseResult.code && downloadUrl" style="margin-top: 10px; text-align: right;">-->
<!--      <el-button type="primary" @click="copyShowFileLink">分享文件直链</el-button>-->
<!--    </div>-->
    <!-- 目录解析结果区下方加分享按钮 -->
<!--    <div v-if="showDirectoryTree && directoryData.length" style="margin-top: 10px; text-align: right;">-->
<!--      <el-input :value="showListLink" readonly style="width: 350px; margin-right: 10px;">-->
<!--        <template #append>-->
<!--          <el-button v-clipboard:copy="showListLink" v-clipboard:success="onCopy" v-clipboard:error="onError">-->
<!--            <el-icon><CopyDocument /></el-icon>复制分享链接-->
<!--          </el-button>-->
<!--        </template>-->
<!--      </el-input>-->
<!--    </div>-->

  </div>

</template>

<script>
import axios from 'axios'
import QRCode from 'qrcode'
import DarkMode from '@/components/DarkMode'
import DirectoryTree from '@/components/DirectoryTree'
import parserUrl from '../parserUrl1'
import fileTypeUtils from '@/utils/fileTypeUtils'
import { ElMessage } from 'element-plus'

export const previewBaseUrl = 'https://nfd-parser.github.io/nfd-preview/preview.html?src=';

export default {
  name: 'App',
  components: { DarkMode, DirectoryTree },
  mixins: [fileTypeUtils],
  data() {
    return {
      baseAPI: `${location.protocol}//${location.host}`,
      autoReadClipboard: true,
      isDarkMode: false,
      isLoading: false,
      
      // 输入数据
      link: "",
      password: "",
      
      // 解析结果
      parseResult: {},
      downloadUrl: null,
      directLink: '',
      previewBaseUrl,
      
      // 功能结果
      markdownText: '',
      showQRCode: false,
      qrCodeUrl: '',
      statisticsData: {},
      
      // 目录树
      showDirectoryTree: false,
      directoryData: [],
      
      // 统计信息
      node1Info: {},
      node2Info: {},
      hasWarnedNoLink: false,
      directoryViewMode: 'pane', // 新增，目录树展示模式
      hasClipboardSuccessTip: false, // 新增，聚焦期间只提示一次
      showRiskDialog: false,
      baseUrl: location.origin,
      showListLink: '',

      errorDialogVisible: false,
      errorDetail: null,
      errorButtonVisible: false,
      
      // 版本信息
      buildVersion: ''
    }
  },
  methods: {
    // 主题切换
    handleThemeChange(isDark) {
      this.isDarkMode = isDark
      document.body.classList.toggle('dark-theme', isDark)
      window.localStorage.setItem('isDarkMode', isDark)

    },

    // 验证输入
    validateInput() {
      this.clearResults()
      
      if (!this.link.startsWith("https://") && !this.link.startsWith("http://")) {
        this.$message.error("请输入有效链接!")
        throw new Error('请输入有效链接')
      }
    },

    // 清除结果
    clearResults() {
      this.parseResult = {}
      this.downloadUrl = null
      this.markdownText = ''
      this.showQRCode = false
      this.statisticsData = {}
      this.showDirectoryTree = false
      this.directoryData = []
    },

    // 统一API调用
    async callAPI(endpoint, params = {}) {
      this.errorButtonVisible = false
      try {
        this.isLoading = true
        const response = await axios.get(`${this.baseAPI}${endpoint}`, { params })
        
        if (response.data.code === 200) {
          // this.$message.success(response.data.msg || '操作成功')
          return response.data
        } else {
          // 在页面右下角显示一个“查看详情”按钮 可以查看原json
          this.errorDetail = response?.data
          this.errorButtonVisible = true
          throw new Error(response.data.msg || '操作失败')
        }
      } catch (error) {
        this.$message.error(error.message || '网络错误')
        throw error
      } finally {
        this.isLoading = false
      }
    },

    // 文件解析
    async parseFile() {
      try {
        this.validateInput()
        const params = { url: this.link }
        if (this.password) params.pwd = this.password
        
        const result = await this.callAPI('/json/parser', params)
        this.parseResult = result
        this.downloadUrl = result.data?.directLink
        this.directLink = `${this.baseAPI}/parser?url=${this.link}${this.password ? `&pwd=${this.password}` : ''}`
        this.$message.success('文件解析成功！')
      } catch (error) {
        console.error('文件解析失败:', error)
      }
    },

    // 目录解析
    async parseDirectory() {
      try {
        this.validateInput()
        const params = { url: this.link }
        if (this.password) params.pwd = this.password
        
        const result = await this.callAPI('/v2/linkInfo', params)
        const data = result.data
        
        // 检查是否支持目录解析
        const supportedPans = ["iz", "lz", "fj", "ye"]
        if (!supportedPans.includes(data.shareLinkInfo.type)) {
          this.$message.error("当前网盘不支持目录解析")
          return
        }

        // 获取目录数据
        const directoryResult = await this.callAPI('/v2/getFileList', params)
        this.directoryData = directoryResult.data || []
        this.showDirectoryTree = true
        // 自动赋值分享链接
        this.showListLink = `${this.baseUrl}/showList?url=${encodeURIComponent(this.link)}`

        this.$message.success(`目录解析成功！共找到 ${this.directoryData.length} 个文件/文件夹`)
      } catch (error) {
        console.error('目录解析失败:', error)
      }
    },

    // 生成Markdown
    async generateMarkdown() {
      try {
        this.validateInput()
        const params = { url: this.link }
        if (this.password) params.pwd = this.password
        
        const result = await this.callAPI('/v2/linkInfo', params)
        this.markdownText = this.buildMarkdown('快速下载地址', result.data.downLink)
        this.$message.success('Markdown生成成功！')
      } catch (error) {
        console.error('生成Markdown失败:', error)
      }
    },

    // 生成二维码
    async generateQRCode() {
      try {
        this.validateInput()
        const params = { url: this.link }
        if (this.password) params.pwd = this.password
        
        const result = await this.callAPI('/v2/linkInfo', params)
        this.qrCodeUrl = result.data.downLink
        
        const options = {
          width: 150,
          height: 150,
          margin: 2
        }
        
        QRCode.toCanvas(this.$refs.qrcodeCanvas, this.qrCodeUrl, options, error => {
          if (error) console.error(error)
        })
        
        this.showQRCode = true
        this.$message.success('二维码生成成功！')
      } catch (error) {
        console.error('生成二维码失败:', error)
      }
    },

    // 获取统计信息
    async getStatistics() {
      try {
        this.validateInput()
        const params = { url: this.link }
        if (this.password) params.pwd = this.password
        
        const result = await this.callAPI('/v2/linkInfo', params)
        this.statisticsData = result.data
        this.$message.success('统计信息获取成功！')
      } catch (error) {
        console.error('获取统计信息失败:', error)
      }
    },

    // 构建Markdown链接
    buildMarkdown(title, url) {
      return `[${title}](${url})`
    },

    // 复制成功
    onCopy() {
      this.$message.success('复制成功')
    },

    // 复制失败
    onError() {
      this.$message.error('复制失败')
    },

    // 文件点击处理
    handleFileClick(file) {
      if (file.parserUrl) {
        window.open(file.parserUrl, '_blank')
      } else {
        this.$message.warning('该文件暂无下载链接')
      }
    },

    // 获取剪切板内容
    async getPaste(isManual = false) {
      try {
        const text = await navigator.clipboard.readText()
        console.log('获取到的文本内容是：', text)
        
        const linkInfo = parserUrl.parseLink(text)
        const pwd = parserUrl.parsePwd(text) || ''
        
        if (linkInfo.link) {
          if (linkInfo.link !== this.link || pwd !== this.password) {
            this.password = pwd
            this.link = linkInfo.link
            this.directLink = `${this.baseAPI}/parser?url=${this.link}${this.password ? `&pwd=${this.password}` : ''}`
            // 聚焦期间只提示一次
            if (!this.hasClipboardSuccessTip) {
              this.$message.success(`自动识别分享成功, 网盘类型: ${linkInfo.name}; 分享URL ${this.link}; 分享密码: ${this.password || '空'}`)
              this.hasClipboardSuccessTip = true
            }
          } else {
            this.$message.warning(`[${linkInfo.name}]分享信息无变化`)
          }
          this.hasWarnedNoLink = false // 有效链接后重置
        } else {
          if (isManual || !this.hasWarnedNoLink) {
            this.$message.warning("未能提取到分享链接, 该分享可能尚未支持, 你可以复制任意网盘/音乐App的分享到该页面, 系统智能识别")
            this.hasWarnedNoLink = true
          }
        }
      } catch (error) {
        console.error('读取剪切板失败:', error)
        this.$message.error('读取剪切板失败，请检查浏览器权限')
      }
    },

    // 获取统计信息
    async getInfo() {
      try {
        const response = await axios.get('/v2/statisticsInfo')
        if (response.data.success) {
          this.node1Info = response.data.data
        }
      } catch (error) {
        console.error('获取统计信息失败:', error)
      }
    },

    // 获取版本号
    async getBuildVersion() {
      try {
        const response = await axios.get('/v2/build-version')
        this.buildVersion = response.data.data
      } catch (error) {
        console.error('获取版本号失败:', error)
        this.buildVersion = 'unknown'
      }
    },

    // 新增切换目录树展示模式方法
    setDirectoryViewMode(mode) {
      this.directoryViewMode = mode
    },

    // 文件名和类型提取方法（复用 DirectoryTree 的静态方法）
    extractFileNameAndExt(url) {
      return fileTypeUtils.extractFileNameAndExt(url)
    },
    getFileTypeClass(file) {
      return fileTypeUtils.getFileTypeClass(file)
    },
    ackRisk() {
      window.localStorage.setItem('nfd_risk_ack', '1')
      this.showRiskDialog = false
    },
    copyShowFileLink() {
      const url = `${this.baseUrl}/showFile?url=${encodeURIComponent(this.downloadUrl)}`
      navigator.clipboard.writeText(url).then(() => {
        ElMessage.success('文件分享链接已复制！')
      })
    },
    copyShowListLink() {
      const url = `${this.baseUrl}/showList?url=${encodeURIComponent(this.link)}`
      navigator.clipboard.writeText(url).then(() => {
        ElMessage.success('目录分享链接已复制！')
      })
    },

    copyErrorDetails() {
      const text = `分享链接：${this.link}
分享密码：${this.password || ''}
错误信息：${JSON.stringify(this.errorDetail, null, 2)}`;
      navigator.clipboard.writeText(text).then(() => {
        this.$message.success('已复制分享信息和错误详情');
        window.open('https://github.com/qaiu/netdisk-fast-download/issues/new', '_blank');
      }).catch(() => {
        this.$message.error('复制失败');
      });
    }
  },
  
  mounted() {
    // 从localStorage读取设置
    const savedAutoRead = window.localStorage.getItem("autoReadClipboard")
    if (savedAutoRead !== null) {
      this.autoReadClipboard = savedAutoRead === 'true'
    }

    // 获取初始统计信息
    this.getInfo()

    // 获取版本号
    this.getBuildVersion()

    // 自动读取剪切板
    if (this.autoReadClipboard) {
      this.getPaste()
    }

    // 监听窗口焦点事件
    window.addEventListener('focus', () => {
      if (this.autoReadClipboard) {
        this.hasClipboardSuccessTip = false // 聚焦时重置，只提示一次
        this.getPaste()
      }
    })

    // 首次打开页面弹出风险提示
    if (!window.localStorage.getItem('nfd_risk_ack')) {
      this.showRiskDialog = true
    }
  },
  
  watch: {
    downloadUrl(val) {
      if (!val) {
        this.$router.push('/')
      }
    },
    autoReadClipboard(val) {
      window.localStorage.setItem("autoReadClipboard", val)
    }
  }
}
</script>

<style>
[v-cloak] { display: none; }

body {
  background-color: #f5f7fa;
  color: #2c3e50;
  margin: 0;
  padding: 0;
}
body.dark-theme {
  background-color: #181818;
  color: #ffffff;
}

#app {
  /* 不设置 background-color */
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  margin: auto;
  padding: 1em;
  max-width: 900px;
}

#app.dark-theme {
  /* 不设置 background-color */
  color: #ffffff;
}

.box-card {
  flex: 1;
  margin-top: 4em !important;
  margin-bottom: 4em !important;
  opacity: 1 !important; /* 只要不透明 */
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.08);
  border: none;
}

#app.dark-theme .box-card {
  background: #232323 !important;
  color: #fff !important;
  box-shadow: 0 10px 30px rgba(0,0,0,0.3);
  border: none;
}

@media screen and (max-width: 700px) {
  #app {
    padding-left: 0 !important;
    padding-right: 0 !important;
    margin: 0 !important; /* 关键：去掉 auto 居中 */
    max-width: 100vw !important;
  }
  #app .box-card {
    margin: 1em 4px !important; /* 上下1em，左右4px */
    width: auto !important;
    max-width: 100vw !important;
    box-sizing: border-box;
  }
}

.grid-content {
  margin-top: 1em;
  border-radius: 4px;
  min-height: 50px;
}

.el-select .el-input {
  width: 130px;
}

.directory-tree-container {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background-color: #fafafa;
}

#app.dark-theme .directory-tree-container {
  background-color: #2d2d2d;
  border-color: #404040;
}

.download h3 {
  margin-top: 2em;
}

.download button {
  margin-right: 0.5em;
  margin-left: 0.5em;
}

.typo {
  text-align: left;
}

.typo a {
  color: #0077ff;
}

#app.dark-theme .typo a {
  color: #4a9eff;
}

hr {
  height: 10px;
  margin-bottom: .8em;
  border: none;
  border-bottom: 1px solid rgba(0, 0, 0, .12);
}

#app.dark-theme hr {
  border-bottom-color: rgba(255, 255, 255, .12);
}

.feedback-bar {
  width: 100%;
  text-align: right;
  padding: 10px 10px 0 0;
}
.feedback-link {
  color: #888;
  font-weight: 500;
  font-size: 0.98rem;
  text-decoration: none;
  border: none;
  border-radius: 5px;
  padding: 2px 10px;
  background: transparent;
  transition: background 0.2s, color 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 6px;
}
.feedback-link:first-child { margin-left: 0; }
.feedback-link:hover {
  background: #f0f0f0;
  color: #333;
}
.dark-theme .feedback-link {
  background: transparent;
  color: #bbb;
  border: none;
}
.dark-theme .feedback-link:hover {
  background: #333;
  color: #fff;
}
.feedback-link.mini {
  font-size: 0.92rem;
  padding: 2px 8px;
  border-radius: 4px;
}
.feedback-icon {
  font-size: 1em;
  color: #888;
  margin-right: 2px;
}
.feedback-link:hover .feedback-icon {
  color: #333;
}
.feedback-link:nth-child(2) .feedback-icon { color: #333; }
.feedback-link:nth-child(3) .feedback-icon { color: #f39c12; }
.dark-theme .feedback-icon {
  color: #bbb;
}
.dark-theme .feedback-link:nth-child(2) .feedback-icon { color: #fff; }
.dark-theme .feedback-link:nth-child(3) .feedback-icon { color: #f7ca77; }
.feedback-link:nth-child(4) .feedback-icon { color: #409eff; }
.dark-theme .feedback-link:nth-child(4) .feedback-icon { color: #4a9eff; }

.project-intro {
  margin: 0 auto 18px auto;
  max-width: 700px;
  text-align: center;
  color: #888;
  font-size: 1.02rem;
}
.intro-title {
  font-size: 1.18rem;
  font-weight: bold;
  margin-bottom: 4px;
  color: #666;
}
.intro-desc {
  color: #888;
  font-size: 0.98rem;
  line-height: 1.7;
}
.dark-theme .project-intro, .dark-theme .intro-title, .dark-theme .intro-desc {
  color: #bbb;
}
.dark-theme .intro-title {
  color: #eee;
}
.file-meta-info-card {
  margin: 18px auto 0 auto;
  max-width: 600px;
  background: #f8f9fa;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  padding: 18px 24px 12px 24px;
  font-size: 1.02rem;
  color: #333;
}
#app.dark-theme .file-meta-info-card {
  background: #232323;
  color: #eee;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
}
.file-meta-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  font-size: 1.01em;
}
.file-meta-label {
  min-width: 90px;
  color: #888;
  font-weight: 500;
  margin-right: 8px;
}
#app.dark-theme .file-meta-label {
  color: #bbb;
}
.file-meta-link {
  color: #409eff;
  word-break: break-all;
  text-decoration: underline;
}
#app.dark-theme .file-meta-link {
  color: #4a9eff;
}
#app.dark-theme .jv-container {
  background: #232323 !important;
  color: #eee !important;
  border-color: #444 !important;
}
#app.dark-theme .jv-key {
  color: #4a9eff !important;
}
#app.dark-theme .jv-number {
  color: #f39c12 !important;
}
#app.dark-theme .jv-string {
  color: #27ae60 !important;
}
#app.dark-theme .jv-boolean {
  color: #e67e22 !important;
}
#app.dark-theme .jv-null {
  color: #e74c3c !important;
}
#app.jv-container .jv-item.jv-object {
    color: #32ba6d;
}

.feedback-bar {
  width: 100%;
  margin: 0 auto;        /* 居中 */
  text-align: right;
  box-sizing: border-box;
}

@media screen and (max-width: 700px) {
  .feedback-bar {
    max-width: 480px;    /* 和移动端卡片宽度一致 */
    padding-right: 8px;  /* 和卡片内容对齐 */
    padding-left: 8px;
  }
}

.jv-container.jv-light .jv-item.jv-object {
  color: #888;
}

/* 版本号显示样式 */
.version-info {
  text-align: center;
  margin-top: 20px;
  margin-bottom: 20px;
}

.version-text {
  font-size: 0.85rem;
  color: #999;
  font-weight: 400;
}

#app.dark-theme .version-text {
  color: #666;
}
</style>
