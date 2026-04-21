<template>
  <div class="main-container">
    <div class="directory-tree" :class="{ 'dark-theme': isDarkTheme }">
      <template v-if="viewMode === 'pane'">
        <!-- 窗格模式（原有） -->
        <div class="breadcrumb">
          <div 
            v-for="(item, index) in pathStack" 
            :key="index"
            class="breadcrumb-item"
            :class="{ 'active': index === pathStack.length - 1 }"
            @click="goToDirectory(index)"
          >
            <i class="fas fa-folder" v-if="index === 0"></i>
            <i class="fas fa-chevron-right" v-else-if="index > 0"></i>
            {{ item.name }}
          </div>
        </div>
        <div class="file-grid" v-loading="loading">
          <div 
            v-for="file in currentFileList" 
            :key="file.fileName"
            class="file-item"
            :class="[getFileTypeClass(file), { 'selected': batchMode && isFileSelected(file) }]"
            @click="batchMode ? onBatchClick(file) : handleFileClick(file)"
          >
            <div v-if="batchMode && file.fileType !== 'folder'" class="batch-checkbox" @click.stop="toggleFileSelect(file)">
              <i :class="isFileSelected(file) ? 'fas fa-check-square' : 'far fa-square'" 
                 :style="{ color: isFileSelected(file) ? '#409eff' : '#c0c4cc' }"></i>
            </div>
            <div class="file-icon">
              <i :class="getFileIcon(file)"></i>
            </div>
            <div class="file-name">{{ file.fileName }}</div>
            <div class="file-meta">
              <template v-if="file.fileType !== 'folder'">{{ file.sizeStr || '0B' }} · </template>{{ formatDate(file.createTime) }}
            </div>
          </div>
          <div v-if="!loading && (!currentFileList || currentFileList.length === 0)" class="empty-state">
            <i class="fas fa-folder-open"></i>
            <h3>此文件夹为空</h3>
            <p>暂无文件或文件夹</p>
          </div>
        </div>
        <!-- 批量模式 action-bar -->
        <div v-if="batchMode" class="action-bar batch-action-bar">
          <div class="batch-left">
            <el-button size="small" @click="selectAll">全选</el-button>
            <el-button size="small" @click="deselectAll">取消全选</el-button>
            <span class="batch-count">已选 {{ selectedFiles.length }} 个文件</span>
          </div>
          <div class="batch-right">
            <el-button 
              type="primary" size="small"
              :disabled="selectedFiles.length === 0"
              :loading="batchDownloading"
              @click="batchBrowserDownload"
            >
              <i class="fas fa-download"></i> 浏览器下载
            </el-button>
            <el-button 
              type="success" size="small"
              :disabled="selectedFiles.length === 0"
              :loading="batchDownloading"
              @click="batchSendToDownloader"
            >
              <i class="fas fa-paper-plane"></i> 发送到下载器
            </el-button>
            <el-button size="small" @click="toggleBatchMode">取消</el-button>
          </div>
        </div>
        <!-- 正常模式 action-bar -->
        <div v-else class="action-bar">
          <el-button 
            type="primary" 
            @click="goBack" 
            :disabled="pathStack.length <= 1"
            icon="el-icon-arrow-left"
          >
            返回上一级
          </el-button>
          <div class="stats">
            <span class="stat-item">
              <i class="fas fa-folder"></i> {{ folderCount }} 个文件夹
            </span>
            <span class="stat-item">
              <i class="fas fa-file"></i> {{ fileCount }} 个文件
            </span>
            <el-button 
              v-if="fileCount > 0"
              type="warning" size="small"
              @click="toggleBatchMode"
              style="margin-left: 10px;"
            >
              <i class="fas fa-check-double"></i> 批量下载
            </el-button>
          </div>
        </div>
      </template>
      <template v-else-if="viewMode === 'tree'">
        <div class="content-card" :class="{ 'mobile-tree-layout': isMobile }">
          <splitpanes v-if="!isMobile" class="split-theme custom-splitpanes" style="height:100%;">
            <pane>
              <div class="tree-sidebar">
                <div class="tree-scroll-wrap">
                  <el-tree
                    ref="fileTree"
                    :data="treeData"
                    :props="treeProps"
                    node-key="id"
                    lazy
                    :load="loadNode"
                    :highlight-current="!batchMode"
                    :show-checkbox="batchMode"
                    :check-strictly="false"
                    @node-click="onNodeClick"
                    @check-change="onTreeCheckChange"
                    :default-expand-all="false"
                    :default-expanded-keys="['root']"
                    :render-content="renderContent"
                    style="background:transparent;"
                  />
                </div>
                <div v-if="batchMode" class="tree-sidebar-footer">
                  <span class="tree-sidebar-count">已勾选 {{ selectedFiles.length }} 个文件</span>
                  <div class="tree-sidebar-actions">
                    <el-button
                      type="primary"
                      size="small"
                      :disabled="selectedFiles.length === 0 || batchDownloading"
                      :loading="batchDownloading"
                      @click="batchBrowserDownload"
                    >
                      浏览器下载
                    </el-button>
                    <el-button
                      type="success"
                      size="small"
                      :disabled="selectedFiles.length === 0 || batchDownloading"
                      :loading="batchDownloading"
                      @click="batchSendToDownloader"
                    >
                      发送到下载器
                    </el-button>
                    <el-button
                      size="small"
                      @click="toggleBatchMode"
                    >
                      取消
                    </el-button>
                  </div>
                  <div v-if="batchDownloading" class="batch-progress-info">
                    <el-progress :percentage="batchProgressPercent" :status="batchProgressStatus" />
                    <p>{{ batchProgress.current }} / {{ batchProgress.total }}
                      <span v-if="batchProgress.failed > 0" style="color:#f56c6c;"> ({{ batchProgress.failed }} 失败)</span>
                    </p>
                  </div>
                </div>
              </div>
            </pane>
            <pane>
              <div class="tree-content">
                <div v-if="selectedNode" class="file-detail-panel">
                  <div class="file-detail-icon-wrap">
                    <i :class="getFileIcon(selectedNode)" class="file-detail-icon"></i>
                  </div>
                  <h4 class="file-detail-name">{{ selectedNode.fileName }}</h4>
                  <div v-if="selectedNode.fileType !== 'folder'" class="file-detail-meta">
                    <p>类型: {{ getFileTypeClass(selectedNode) }}</p>
                    <p>大小: {{ selectedNode.sizeStr || '0B' }}</p>
                    <p v-if="selectedNode.createTime">创建时间: {{ formatDate(selectedNode.createTime) }}</p>
                  </div>
                  <div class="file-detail-actions">
                    <el-button v-if="selectedNode.parserUrl" size="small" @click="previewFile(selectedNode)">
                      <i class="fas fa-external-link-alt"></i> 打开
                    </el-button>
                    <el-button
                      v-if="selectedNode.parserUrl && selectedNode.fileType !== 'folder'"
                      type="success" size="small"
                      @click="handleDownload(selectedNode)"
                      :loading="downloadLoading"
                    >
                      <i class="fas fa-download"></i> 下载
                    </el-button>
                    <el-button
                      v-if="selectedNode.parserUrl && selectedNode.fileType !== 'folder'"
                      type="primary" size="small"
                      @click="sendSingleToDownloader(selectedNode)"
                      :loading="singleSendLoading"
                    >
                      <i class="fas fa-paper-plane"></i> 发送到下载器
                    </el-button>
                  </div>
                </div>
                <div v-else class="file-detail-empty">
                  <i class="fas fa-hand-pointer" style="font-size:32px;color:#bbb;margin-bottom:12px;"></i>
                  <p>请在左侧选择文件查看详情</p>
                </div>
                <div v-if="!batchMode" class="tree-batch-trigger">
                  <el-button type="warning" size="small" @click="toggleBatchMode">
                    <i class="fas fa-check-double"></i> 批量下载
                  </el-button>
                </div>
              </div>
            </pane>
          </splitpanes>
          <!-- 移动端：上下布局 -->
          <template v-else>
            <div class="mobile-tree-top">
              <div class="tree-scroll-wrap">
                <el-tree
                  ref="fileTree"
                  :data="treeData"
                  :props="treeProps"
                  node-key="id"
                  lazy
                  :load="loadNode"
                  :highlight-current="!batchMode"
                  :show-checkbox="batchMode"
                  :check-strictly="false"
                  @node-click="onNodeClick"
                  @check-change="onTreeCheckChange"
                  :default-expand-all="false"
                  :default-expanded-keys="['root']"
                  :render-content="renderContent"
                  style="background:transparent;"
                />
              </div>
            </div>
            <div class="mobile-tree-bottom">
              <div v-if="selectedNode" class="file-detail-panel">
                <div class="file-detail-icon-wrap">
                  <i :class="getFileIcon(selectedNode)" class="file-detail-icon"></i>
                </div>
                <h4 class="file-detail-name">{{ selectedNode.fileName }}</h4>
                <div v-if="selectedNode.fileType !== 'folder'" class="file-detail-meta">
                  <p>类型: {{ getFileTypeClass(selectedNode) }}</p>
                  <p>大小: {{ selectedNode.sizeStr || '0B' }}</p>
                  <p v-if="selectedNode.createTime">创建时间: {{ formatDate(selectedNode.createTime) }}</p>
                </div>
                <div class="file-detail-actions">
                  <el-button v-if="selectedNode.parserUrl" size="small" @click="previewFile(selectedNode)">
                    <i class="fas fa-external-link-alt"></i> 打开
                  </el-button>
                  <el-button
                    v-if="selectedNode.parserUrl && selectedNode.fileType !== 'folder'"
                    type="success" size="small"
                    @click="handleDownload(selectedNode)"
                    :loading="downloadLoading"
                  >
                    <i class="fas fa-download"></i> 下载
                  </el-button>
                  <el-button
                    v-if="selectedNode.parserUrl && selectedNode.fileType !== 'folder'"
                    type="primary" size="small"
                    @click="sendSingleToDownloader(selectedNode)"
                    :loading="singleSendLoading"
                  >
                    <i class="fas fa-paper-plane"></i> 发送到下载器
                  </el-button>
                </div>
              </div>
              <div v-else class="file-detail-empty">
                <i class="fas fa-hand-pointer" style="font-size:24px;color:#bbb;margin-bottom:8px;"></i>
                <p>请在上方选择文件查看详情</p>
              </div>
              <div v-if="!batchMode" class="mobile-batch-trigger">
                <el-button type="warning" size="small" @click="toggleBatchMode">
                  <i class="fas fa-check-double"></i> 批量下载
                </el-button>
              </div>
              <div v-if="batchMode" class="mobile-batch-footer">
                <span class="tree-sidebar-count">已勾选 {{ selectedFiles.length }} 个文件</span>
                <div class="tree-sidebar-actions">
                  <el-button type="primary" size="small" :disabled="selectedFiles.length === 0 || batchDownloading" :loading="batchDownloading" @click="batchBrowserDownload">浏览器下载</el-button>
                  <el-button type="success" size="small" :disabled="selectedFiles.length === 0 || batchDownloading" :loading="batchDownloading" @click="batchSendToDownloader">发送到下载器</el-button>
                  <el-button size="small" @click="toggleBatchMode">取消</el-button>
                </div>
                <div v-if="batchDownloading" class="batch-progress-info">
                  <el-progress :percentage="batchProgressPercent" :status="batchProgressStatus" />
                  <p>{{ batchProgress.current }} / {{ batchProgress.total }}
                    <span v-if="batchProgress.failed > 0" style="color:#f56c6c;"> ({{ batchProgress.failed }} 失败)</span>
                  </p>
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>
      <!-- 文件操作对话框（窗格模式下） -->
      <el-dialog
        v-if="viewMode === 'pane'"
        title="文件操作"
        v-model="fileDialogVisible"
        width="400px"
        :before-close="closeFileDialog"
      >
        <div class="file-dialog-content">
          <p><strong>{{ selectedFile?.fileName || '未命名文件' }}</strong></p>
          <p class="file-info">
            大小: {{ selectedFile?.sizeStr || '0B' }}<br>
            创建时间: {{ formatDate(selectedFile?.createTime) }}
          </p>
        </div>
        
        <span slot="footer" class="dialog-footer">
          <el-button type="primary" @click="previewFile(selectedFile)">打开</el-button>
          <!-- 弹窗下载按钮 -->
          <el-button
            v-if="selectedFile && selectedFile.parserUrl"
            type="success"
            @click="handleDownload(selectedFile)"
            style="margin-left: 8px;"
            :loading="downloadLoading"
          >
            下载
          </el-button>
          <el-button
            v-if="selectedFile && selectedFile.parserUrl"
            type="primary"
            @click="sendSingleToDownloader(selectedFile)"
            style="margin-left: 8px;"
            :loading="singleSendLoading"
          >
            发送到下载器
          </el-button>
        </span>
      </el-dialog>
      <div v-if="isPreviewing" class="preview-mask">
        <div class="preview-toolbar">
          <el-button size="small" @click="closePreview">关闭预览</el-button>
          <el-button size="small" type="primary" @click="openPreviewInNewTab">新窗口打开</el-button>
        </div>
        <iframe :src="previewUrl" frameborder="0" class="preview-iframe"></iframe>
      </div>
      <!-- 下载器命令弹窗（共享组件） -->
      <DownloadDialog
        v-model:visible="downloadDialogVisible"
        :download-info="downloadInfo"
      />
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import { ElTree, ElMessageBox } from 'element-plus'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import fileTypeUtils from '@/utils/fileTypeUtils'
import DownloadDialog from '@/components/DownloadDialog.vue'
import { testConnection, autoDetect, addDownload, batchAddDownload, getConfig, saveConfig } from '@/utils/downloaderService'

export default {
  name: 'DirectoryTree',
  components: { ElTree, Splitpanes, Pane, DownloadDialog },
  props: {
    fileList: {
      type: Array,
      default: () => []
    },
    shareUrl: {
      type: String,
      required: true
    },
    password: {
      type: String,
      default: ''
    },
    viewMode: {
      type: String,
      default: 'pane' // 'pane' or 'tree'
    }
  },
  data() {
    return {
      loading: false,
      pathStack: [{ name: '全部文件', url: '' }],
      currentFileList: [],
      fileDialogVisible: false,
      selectedFile: null,
      isDarkTheme: false,
      initialized: false,
      // 文件树模式相关
      treeData: [],
      selectedNode: null,
      isPreviewing: false,
      previewUrl: '',
      // 下载器相关
      downloadDialogVisible: false,
      downloadInfo: null,
      downloadLoading: false,
      singleSendLoading: false,
      treeProps: {
        label: 'fileName',
        children: 'children',
        isLeaf: 'isLeaf'
      },
      isMobile: false,
      // 批量下载相关
      batchMode: false,
      selectedFiles: [],
      batchDownloading: false,
      batchProgress: { current: 0, total: 0, failed: 0 }
    }
  },
  computed: {
    folderCount() {
      return this.currentFileList.filter(file => file.fileType === 'folder').length
    },
    fileCount() {
      return this.currentFileList.filter(file => file.fileType !== 'folder').length
    },
    batchProgressPercent() {
      if (this.batchProgress.total === 0) return 0
      return Math.round(this.batchProgress.current / this.batchProgress.total * 100)
    },
    batchProgressStatus() {
      if (this.batchProgress.failed > 0) return 'exception'
      if (this.batchProgress.current >= this.batchProgress.total && this.batchProgress.total > 0) return 'success'
      return ''
    }
  },
  watch: {
    fileList: {
      immediate: true,
      handler(newList) {
        // 根节点children为当前目录下所有文件/文件夹
        this.treeData = [
          {
            id: 'root',
            fileName: '全部文件',
            fileType: 'folder',
            children: (newList || []).map(item => ({
              ...item,
              isLeaf: item.fileType !== 'folder'
            })),
            isLeaf: false
          }
        ]
        this.currentFileList = newList
      }
    }
  },
  methods: {
    ...fileTypeUtils,
    apiKeyHeaders() {
      const headers = {}
      const apiKey = localStorage.getItem('nfd_user_api_key')
      if (apiKey) {
        headers['X-API-Key'] = apiKey
      }
      return headers
    },
    buildApiUrl() {
      const baseUrl = `${window.location.origin}/v2/getFileList`
      const params = new URLSearchParams({
        url: this.shareUrl
      })
      if (this.password) {
        params.append('pwd', this.password)
      }
      return `${baseUrl}?${params.toString()}`
    },
    // 文件树与窗格同源：直接返回当前目录数据
    buildTree(list) {
      return list || []
    },
    // 懒加载子节点
    loadNode(node, resolve) {
      if (node.level === 0) {
        resolve(this.treeData[0].children)
      } else if (node.data.fileType === 'folder' && node.data.parserUrl) {
        axios.get(node.data.parserUrl, { headers: this.apiKeyHeaders() }).then(res => {
          if (res.data.code === 200) {
            const children = (res.data.data || []).map(item => ({
              ...item,
              isLeaf: item.fileType !== 'folder'
            }))
            resolve(children)
          } else {
            resolve([])
          }
        }).catch(() => resolve([]))
      } else {
        resolve([])
      }
    },
    onNodeClick(data) {
      this.selectedNode = data
    },
    // 处理文件点击
    handleFileClick(file) {
      console.log('点击文件', file, this.viewMode)
      if (file.fileType === 'folder') {
        this.enterFolder(file)
      } else if (this.viewMode === 'pane') {
        this.selectedFile = file
        this.fileDialogVisible = true
      }
    },
    // 进入文件夹
    async enterFolder(folder) {
      if (!folder.parserUrl) {
        this.$message.error('无法进入该文件夹，缺少访问链接')
        return
      }
      try {
        this.loading = true
        const response = await axios.get(folder.parserUrl, { headers: this.apiKeyHeaders() })
        if (response.data.code === 200) {
          const newDir = {
            url: folder.parserUrl,
            name: folder.fileName || '未命名文件夹'
          }
          this.pathStack.push(newDir)
          this.currentFileList = response.data.data || []
        } else {
          this.$message.error(response.data.msg || '获取文件夹内容失败')
        }
      } catch (error) {
        console.error('进入文件夹失败:', error)
        this.$message.error('进入文件夹失败')
      } finally {
        this.loading = false
      }
    },
    goBack() {
      if (this.pathStack.length > 1) {
        this.pathStack.pop()
        this.loadCurrentDirectory()
      }
    },
    goToDirectory(index) {
      this.pathStack.splice(index + 1)
      this.loadCurrentDirectory()
    },
    async loadCurrentDirectory() {
      const currentDir = this.pathStack[this.pathStack.length - 1]
      if (!currentDir.url) {
        this.currentFileList = this.fileList
        return
      }
      try {
        this.loading = true
        const response = await axios.get(currentDir.url, { headers: this.apiKeyHeaders() })
        if (response.data.code === 200) {
          this.currentFileList = response.data.data || []
        } else {
          this.$message.error(response.data.msg || '加载目录失败')
        }
      } catch (error) {
        console.error('加载目录失败:', error)
        this.$message.error('加载目录失败')
      } finally {
        this.loading = false
      }
    },
    // 预览文件
    previewFile(file) {
      if (file?.previewUrl || file?.parserUrl) {
        this.previewUrl = this.appendToken(file.previewUrl || file.parserUrl)
        this.isPreviewing = true
      } else {
        this.$message.warning('该文件暂无预览链接')
      }
      this.closeFileDialog()
    },
    appendToken(url) {
      if (!url) return url
      const apiKey = localStorage.getItem('nfd_user_api_key')
      if (!apiKey) return url
      const resolvedUrl = new URL(url, window.location.origin)
      if (resolvedUrl.searchParams.has('token')) return resolvedUrl.toString()
      resolvedUrl.searchParams.set('token', apiKey)
      return resolvedUrl.toString()
    },
    // 下载文件
    downloadFile(file) {
      if (file?.parserUrl) {
        const a = document.createElement('a')
        a.href = this.appendToken(file.parserUrl)
        a.target = '_blank'
        a.rel = 'noopener noreferrer'
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        this.$message.success('开始下载文件')
      } else {
        this.$message.warning('该文件暂无下载链接')
      }
      this.closeFileDialog()
    },
    // 智能下载：判断是否需要下载器
    async handleDownload(file) {
      if (!file || !file.parserUrl) {
        this.$message.warning('该文件暂无下载链接')
        return
      }
      // 检查 extParameters 中是否标记了 needDownloader
      const needDownloader = file.extParameters && file.extParameters.needDownloader
      if (!needDownloader) {
        // 不需要下载器，直接跳转下载
        this.downloadFile(file)
        return
      }
      // 需要下载器，调用 getFileDownInfo 接口获取下载信息
      this.downloadLoading = true
      try {
        // 从 parserUrl 提取 type 和 param
        // parserUrl 格式: /v2/redirectUrl/{type}/{param} 或 完整URL
        const url = new URL(file.parserUrl, window.location.origin)
        const pathParts = url.pathname.split('/')
        // 找到 redirectUrl 后面的部分
        const redirectIdx = pathParts.indexOf('redirectUrl')
        if (redirectIdx === -1 || redirectIdx + 2 >= pathParts.length) {
          this.$message.error('无法解析下载参数')
          return
        }
        const type = pathParts[redirectIdx + 1]
        const param = pathParts[redirectIdx + 2]
        
        const headers = {}
        const apiKey = localStorage.getItem('nfd_user_api_key')
        if (apiKey) {
          headers['X-API-Key'] = apiKey
        }
        const response = await axios.get(`${window.location.origin}/v2/getFileDownInfo/${type}/${param}`, { headers })
        if (response.data.code === 200 && response.data.data) {
          const info = response.data.data
          if (info.needDownloader) {
            this.downloadInfo = info
            this.downloadDialogVisible = true
          } else if (info.downloadUrl) {
            // 服务端判断不需要下载器，直接跳转
            const a = document.createElement('a')
            a.href = info.downloadUrl
            a.target = '_blank'
            a.rel = 'noopener noreferrer'
            document.body.appendChild(a)
            a.click()
            document.body.removeChild(a)
            this.$message.success('开始下载文件')
          } else {
            this.downloadFile(file)
          }
        } else {
          this.downloadFile(file)
        }
      } catch (error) {
        console.error('获取下载信息失败:', error)
        this.$message.error('获取下载信息失败，尝试直接下载')
        this.downloadFile(file)
      } finally {
        this.downloadLoading = false
        this.closeFileDialog()
      }
    },
    async ensureDownloaderConnected() {
      const config = getConfig()
      // 迅雷直接检测 JS-SDK
      if (config.downloaderType === 'thunder') {
        const result = await testConnection()
        if (!result.connected) {
          this.$message.error('迅雷客户端未检测到，请确认已安装并启动迅雷')
          return false
        }
        return true
      }
      // 先尝试已保存的配置
      const result = await testConnection()
      if (result.connected) return true
      // 连接失败，自动检测
      const detected = await autoDetect(config.rpcSecret)
      if (detected.found) {
        saveConfig({ ...config, downloaderType: detected.type, rpcUrl: detected.rpcUrl })
        this.$message.success(`自动检测到 ${detected.type} ${detected.version}`)
        return true
      }
      try {
        await ElMessageBox.confirm(
          '未检测到本地下载器，是否切换为迅雷下载？',
          '下载器未检测到',
          {
            confirmButtonText: '使用迅雷',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        const thunderConfig = {
          ...config,
          downloaderType: 'thunder',
          rpcUrl: ''
        }
        saveConfig(thunderConfig)
        const thunderResult = await testConnection()
        if (thunderResult.connected) {
          this.$message.success('已切换并保存为迅雷下载器配置')
          return true
        }
        this.$message.error('已保存为迅雷配置，但未检测到迅雷客户端，请先启动迅雷')
      } catch {
        this.$message.error('下载器连接失败，请先在首页设置中配置下载器')
      }
      return false
    },
    async sendSingleToDownloader(file) {
      if (!file || !file.parserUrl) {
        this.$message.warning('该文件暂无下载链接')
        return
      }
      if (!await this.ensureDownloaderConnected()) return
      this.singleSendLoading = true
      try {
        const tp = this.extractTypeParam(file)
        if (tp && file.extParameters && file.extParameters.needDownloader) {
          const headers = this.apiKeyHeaders()
          const resp = await axios.get(
            `${window.location.origin}/v2/getFileDownInfo/${tp.type}/${tp.param}`,
            { headers }
          )
          const info = resp.data.data || resp.data
          if (info && info.downloadUrl) {
            await addDownload(info.downloadUrl, info.downloadHeaders || {}, file.fileName)
            this.$message.success('已发送到下载器')
          } else {
            this.$message.error('获取下载信息失败')
          }
        } else {
          const rawUrl = file.parserUrl.startsWith('http') ? file.parserUrl : (window.location.origin + file.parserUrl)
          const url = this.appendToken(rawUrl)
          const headers = (file.extParameters && file.extParameters.downloadHeaders) || {}
          await addDownload(url, headers, file.fileName)
          this.$message.success('已发送到下载器')
        }
      } catch (error) {
        console.error('发送到下载器失败:', error)
        this.$message.error('发送到下载器失败: ' + error.message)
      } finally {
        this.singleSendLoading = false
      }
    },
    closeFileDialog() {
      this.fileDialogVisible = false
      this.selectedFile = null
    },
    closePreview() {
      this.isPreviewing = false
      this.previewUrl = ''
    },
    openPreviewInNewTab() {
      if (this.previewUrl) {
        // 使用动态 a 标签并设置 noopener noreferrer 以避免携带 Referer，绕过防盗链机制
        const a = document.createElement('a')
        a.href = this.previewUrl
        a.target = '_blank'
        a.rel = 'noopener noreferrer'
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
      }
    },
    formatDate(timestamp) {
      if (!timestamp) return '未知时间'
      const date = new Date(timestamp)
      return date.toLocaleString('zh-CN')
    },
    checkTheme() {
      this.isDarkTheme = document.documentElement.classList.contains('dark')
    },
    checkMobile() {
      this.isMobile = window.innerWidth <= 768
    },
    // ===== 批量下载方法 =====
    toggleBatchMode() {
      this.batchMode = !this.batchMode
      this.selectedFiles = []
      this.batchDownloading = false
      this.batchProgress = { current: 0, total: 0, failed: 0 }
      // tree 模式下清除勾选
      if (this.$refs.fileTree) {
        this.$refs.fileTree.setCheckedKeys([])
      }
    },
    isFileSelected(file) {
      return this.selectedFiles.some(f => f.fileName === file.fileName && f.parserUrl === file.parserUrl)
    },
    toggleFileSelect(file) {
      if (file.fileType === 'folder') return
      const idx = this.selectedFiles.findIndex(f => f.fileName === file.fileName && f.parserUrl === file.parserUrl)
      if (idx >= 0) {
        this.selectedFiles.splice(idx, 1)
      } else {
        this.selectedFiles.push(file)
      }
    },
    onBatchClick(file) {
      if (file.fileType === 'folder') {
        this.enterFolder(file)
        return
      }
      this.toggleFileSelect(file)
    },
    selectAll() {
      this.selectedFiles = this.currentFileList.filter(f => f.fileType !== 'folder')
    },
    deselectAll() {
      this.selectedFiles = []
    },
    onTreeCheckChange() {
      if (!this.$refs.fileTree) return
      const checked = this.$refs.fileTree.getCheckedNodes()
      this.selectedFiles = checked.filter(n => n.fileType !== 'folder' && n.parserUrl)
    },
    extractTypeParam(file) {
      if (!file.parserUrl) return null
      try {
        const url = new URL(file.parserUrl, window.location.origin)
        const parts = url.pathname.split('/')
        const idx = parts.indexOf('redirectUrl')
        if (idx === -1 || idx + 2 >= parts.length) return null
        return { type: parts[idx + 1], param: parts[idx + 2] }
      } catch {
        return null
      }
    },
    async batchBrowserDownload() {
      if (this.selectedFiles.length === 0) return
      this.batchDownloading = true
      this.batchProgress = { current: 0, total: this.selectedFiles.length, failed: 0 }
      for (const file of this.selectedFiles) {
        try {
          const a = document.createElement('a')
          const rawUrl = file.parserUrl.startsWith('http') ? file.parserUrl : (window.location.origin + file.parserUrl)
          a.href = this.appendToken(rawUrl)
          a.target = '_blank'
          a.rel = 'noopener noreferrer'
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          await new Promise(r => setTimeout(r, 500))
        } catch {
          this.batchProgress.failed++
        }
        this.batchProgress.current++
      }
      this.batchDownloading = false
      const failed = this.batchProgress.failed
      if (failed === 0) {
        this.$message.success(`已发起 ${this.batchProgress.total} 个文件下载`)
      } else {
        this.$message.warning(`${this.batchProgress.total - failed} 成功，${failed} 失败`)
      }
    },
    async batchSendToDownloader() {
      if (this.selectedFiles.length === 0) return
      // 先检测下载器连接（含自动检测）
      if (!await this.ensureDownloaderConnected()) return

      this.batchDownloading = true
      const total = this.selectedFiles.length
      this.batchProgress = { current: 0, total, failed: 0 }

      // 所有文件统一发 parserUrl(302) + downloadHeaders 给下载器
      const downloadTasks = []
      for (const file of this.selectedFiles) {
        const rawUrl = file.parserUrl.startsWith('http') ? file.parserUrl : (window.location.origin + file.parserUrl)
        const url = this.appendToken(rawUrl)
        const headers = (file.extParameters && file.extParameters.downloadHeaders) || {}
        downloadTasks.push({ url, headers, fileName: file.fileName })
        this.batchProgress.current++
      }

      // 批量发送所有链接到下载器（aria2 用 system.multicall，gopeed 用 batch API）
      const batchResult = await batchAddDownload(downloadTasks)
      this.batchProgress.failed += batchResult.failed
      if (batchResult.errors.length > 0) {
        console.error('批量发送失败详情:', batchResult.errors)
      }

      this.batchDownloading = false
      const failed = this.batchProgress.failed
      const succeeded = total - failed
      if (failed === 0) {
        this.$message.success(`已发送 ${total} 个文件到下载器`)
      } else if (succeeded > 0) {
        this.$message.warning(`${succeeded} 成功，${failed} 失败`)
      } else {
        this.$message.error('全部发送失败')
      }
    },
    renderContent(h, { node, data, store }) {
      const isFolder = data.fileType === 'folder'
      return h('div', {
        class: 'custom-tree-node'
      }, [
        h('i', {
          class: [this.getFileIcon(data), { 'folder-icon': isFolder, 'file-icon': !isFolder }]
        }),
        h('span', {
          class: ['node-label', { 'folder-text': isFolder, 'file-text': !isFolder }]
        }, node.label)
      ])
    }
  },
  mounted() {
    this.checkTheme()
    this.checkMobile()
    this.initialized = true
    
    this._onResize = () => this.checkMobile()
    window.addEventListener('resize', this._onResize)

    this._observer = new MutationObserver(() => {
      this.checkTheme()
    })
    this._observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    })
  },
  beforeUnmount() {
    if (this._observer) {
      this._observer.disconnect()
    }
    if (this._onResize) {
      window.removeEventListener('resize', this._onResize)
    }
  }
}
</script>

<style>
html, body, #app, .main-container, .directory-tree, .content-card {
  /* overflow: hidden; */
  /* overflow: auto; */
  /* position: relative; */
}
.main-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.directory-tree {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  transition: all 0.3s ease;
  border: 1px solid #eaeaea;
}

.directory-tree.dark-theme {
  background: #2d2d2d;
  color: #ffffff;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  border: 1px solid #404040;
}

.breadcrumb {
  background: #f8f9fa;
  padding: 16px 24px;
  border-bottom: 1px solid #eaeaea;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.dark-theme .breadcrumb {
  background: #404040;
  border-bottom-color: #555555;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  font-size: 0.95rem;
  color: #7f8c8d;
  cursor: pointer;
  transition: color 0.2s;
  margin-right: 8px;
}

.breadcrumb-item:hover {
  color: #3498db;
}

.breadcrumb-item.active {
  color: #2c3e50;
  font-weight: 600;
}

.dark-theme .breadcrumb-item.active {
  color: #ffffff;
}

.breadcrumb-item i {
  margin: 0 8px;
  font-size: 0.8rem;
  color: #bdc3c7;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  padding: 12px;
  min-height: 200px;
  background: #fafafa;
}

.dark-theme .file-grid {
  background: #2d2d2d;
}

.file-item {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 3px 15px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  cursor: pointer;
  text-align: center;
  padding: 10px 4px;
  min-height: 80px;
  border: 2px solid transparent;
}

.dark-theme .file-item {
  background: #404040;
  box-shadow: 0 3px 15px rgba(0, 0, 0, 0.2);
}

.file-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
  border-color: #3498db;
}

.dark-theme .file-item:hover {
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.3);
}

.file-icon {
  font-size: 2.2rem;
  margin-bottom: 8px;
  transition: transform 0.3s;
}

.file-item:hover .file-icon {
  transform: scale(1.1);
}

.folder .file-icon {
  color: #3498db;
}

.image .file-icon {
  color: #e74c3c;
}

.document .file-icon {
  color: #f39c12;
}

.archive .file-icon {
  color: #9b59b6;
}

.audio .file-icon {
  color: #1abc9c;
}

.video .file-icon {
  color: #d35400;
}

.code .file-icon {
  color: #27ae60;
}

.file-name {
  font-weight: 500;
  font-size: 0.85rem;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  margin-bottom: 4px;
}

.file-meta {
  font-size: 0.75rem;
  color: #95a5a6;
}

.dark-theme .file-meta {
  color: #bdc3c7;
}

.empty-state {
  text-align: center;
  padding: 30px 10px;
  color: #7f8c8d;
  grid-column: 1 / -1;
}

.dark-theme .empty-state {
  color: #bdc3c7;
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 10px;
  color: #bdc3c7;
}

.dark-theme .empty-state i {
  color: #555555;
}

.empty-state h3 {
  font-size: 1.1rem;
  margin-bottom: 6px;
  color: #2c3e50;
}

.dark-theme .empty-state h3 {
  color: #ffffff;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8f9fa;
  border-top: 1px solid #eaeaea;
}

.dark-theme .action-bar {
  background: #404040;
  border-top-color: #555555;
}

/* 批量选中样式 */
.file-item.selected {
  background: #ecf5ff !important;
  border-color: #409eff !important;
}
.dark-theme .file-item.selected {
  background: #2a4a6b !important;
  border-color: #409eff !important;
}
.batch-checkbox {
  position: absolute;
  top: 6px;
  left: 6px;
  font-size: 18px;
  z-index: 2;
  cursor: pointer;
}
.file-item {
  position: relative;
}
.batch-action-bar {
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.batch-left, .batch-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.batch-count {
  font-size: 0.85rem;
  color: #606266;
}
.dark-theme .batch-count {
  color: #bdc3c7;
}
/* tree 模式批量面板 */
.tree-batch-panel {
  padding: 20px;
}
.tree-batch-panel h4 {
  margin: 0 0 12px 0;
  color: #409eff;
}
.batch-count-info {
  margin-bottom: 16px;
  color: #606266;
  font-size: 14px;
}
.dark-theme .batch-count-info {
  color: #bdc3c7;
}
.tree-batch-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.batch-progress-info {
  margin-top: 12px;
}
.tree-batch-trigger {
  position: absolute;
  bottom: 12px;
  right: 12px;
}

.stats {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #7f8c8d;
  font-size: 0.85rem;
}

.dark-theme .stats {
  color: #bdc3c7;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.file-dialog-content {
  text-align: center;
}

.file-info {
  color: #7f8c8d;
  font-size: 0.9rem;
  margin-top: 10px;
}

.dark-theme .file-info {
  color: #bdc3c7;
}

.tree-layout {
  display: flex;
  height: 500px;
}
.directory-tree.dark-theme .tree-sidebar {
  background: #232323;
  border-right: 1px solid #404040;
}
.file-tree-root, .tree-node ul {
  list-style: none;
  padding-left: 12px;
  margin: 0;
}
.tree-node {
  margin-bottom: 2px;
}
.tree-node.selected > .tree-node-label {
  background: #e6f7ff;
  color: #409eff;
}
.directory-tree.dark-theme .tree-node.selected > .tree-node-label {
  background: #333c4d;
  color: #4a9eff;
}
.tree-node-label {
  cursor: pointer;
  padding: 3px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.2s;
  font-size: 0.95em;
}
.tree-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  position: relative;
}

/* 自定义树节点样式 */
.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  width: 100%;
}

.custom-tree-node i {
  font-size: 16px;
  width: 20px;
  text-align: center;
  color: #606266;
}

.dark-theme .custom-tree-node i {
  color: #bdc3c7;
}

.custom-tree-node .node-label {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.dark-theme .custom-tree-node .node-label {
  color: #e1e1e1;
}

/* 文件夹样式 */
.custom-tree-node .folder-icon {
  color: #409eff !important;
}

.dark-theme .custom-tree-node .folder-icon {
  color: #4a9eff !important;
}

.custom-tree-node .folder-text {
  color: #409eff !important;
  font-weight: 500;
}

.dark-theme .custom-tree-node .folder-text {
  color: #4a9eff !important;
}

/* 文件样式 */
.custom-tree-node .file-icon {
  color: #95a5a6 !important;
}

.dark-theme .custom-tree-node .file-icon {
  color: #bdc3c7 !important;
}

.custom-tree-node .file-text {
  color: #606266 !important;
}

.dark-theme .custom-tree-node .file-text {
  color: #e1e1e1 !important;
}

/* 特殊文件类型图标颜色 */
.custom-tree-node i.fa-file-image {
  color: #e74c3c !important;
}

.custom-tree-node i.fa-file-pdf {
  color: #e74c3c !important;
}

.custom-tree-node i.fa-file-word {
  color: #3498db !important;
}

.custom-tree-node i.fa-file-excel {
  color: #27ae60 !important;
}

.custom-tree-node i.fa-file-powerpoint {
  color: #f39c12 !important;
}

.custom-tree-node i.fa-file-archive {
  color: #9b59b6 !important;
}

.custom-tree-node i.fa-file-audio {
  color: #1abc9c !important;
}

.custom-tree-node i.fa-file-video {
  color: #d35400 !important;
}

.custom-tree-node i.fa-file-code {
  color: #27ae60 !important;
}

/* 树节点悬停效果 */
.el-tree-node__content:hover .custom-tree-node {
  background-color: #f5f7fa;
  border-radius: 4px;
}

.dark-theme .el-tree-node__content:hover .custom-tree-node {
  background-color: #2c2c2c;
}

/* 选中节点样式 */
.el-tree-node.is-current > .el-tree-node__content .custom-tree-node {
  background-color: #e6f7ff;
  border-radius: 4px;
}

.dark-theme .el-tree-node.is-current > .el-tree-node__content .custom-tree-node {
  background-color: #333c4d;
}

.preview-mask { position: fixed; z-index: 9999; left: 0; top: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.85); display: flex; flex-direction: column; }
.preview-toolbar { padding: 12px; background: #232323; text-align: right; }
.preview-iframe { flex: 1; width: 100vw; border: none; background: #222; }

.content-card {
  min-height: 500px;
  height: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  margin: 0 0 12px 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #eaeaea;
}
.dark-theme .content-card {
  background: #232323;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
  border: 1px solid #404040;
}

.split-theme {
  flex: 1 1 0;
  height: 100%;
}

.custom-splitpanes {
  min-height: 0;
}

.custom-splitpanes .splitpanes__pane {
  min-width: 0;
  min-height: 0;
  display: flex;
}

.custom-splitpanes .splitpanes__splitter {
  background: #e5e7eb;
  width: 8px;
}

.dark-theme .custom-splitpanes .splitpanes__splitter {
  background: #3f4650;
}

.tree-scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 8px;
}

.tree-scroll-wrap .el-tree-node__content {
  overflow: hidden;
}

.tree-scroll-wrap .custom-tree-node .node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-sidebar-footer {
  flex-shrink: 0;
  border-top: 1px solid #eaeaea;
  padding: 10px;
  background: #f8f9fa;
}

.dark-theme .tree-sidebar-footer {
  border-top-color: #404040;
  background: #232323;
}

.tree-sidebar-count {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.dark-theme .tree-sidebar-count {
  color: #bdc3c7;
}

.tree-sidebar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tree-sidebar {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8f9fa;
  border-right: 1px solid #eaeaea;
}

.tree-content {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: 24px 16px 16px 16px;
  align-items: center;
  position: relative;
}

.file-detail-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 320px;
  padding-top: 20px;
}

.file-detail-name {
  text-align: center;
  word-break: break-all;
  margin: 8px 0 12px;
  font-size: 15px;
}

.file-detail-meta {
  width: 100%;
  margin-bottom: 16px;
}

.file-detail-meta p {
  margin: 4px 0;
  font-size: 13px;
  color: #606266;
}

.dark-theme .file-detail-meta p {
  color: #bdc3c7;
}

.file-detail-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.file-detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #aaa;
  font-size: 14px;
}

.file-detail-icon-wrap {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}
.file-detail-icon {
  font-size: 48px;
  color: #409eff;
  display: block;
}
.dark-theme .file-detail-icon {
  color: #4a9eff;
}

/* splitpanes 拖拽条自定义按钮 */
.custom-splitpanes .splitpanes__splitter {
  position: relative;
  background: #e0e0e0;
  transition: background 0.2s;
  touch-action: pan-x pan-y;
}
.dark-theme .custom-splitpanes .splitpanes__splitter {
  background: #404040;
}
.custom-splitpanes .splitpanes__splitter:hover {
  background: #b3b3b3;
}
.dark-theme .custom-splitpanes .splitpanes__splitter:hover {
  background: #555555;
}
.custom-splitpanes .splitpanes__splitter:after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  border: 1.5px solid #d0d0d0;
  z-index: 2;
  display: block;
}
.dark-theme .custom-splitpanes .splitpanes__splitter:after {
  background: #232323;
  border-color: #444;
}

.feedback-bar {
  width: 100%;
  text-align: right;
  padding: 12px 18px 0 0;
}
.feedback-link {
  color: #e74c3c;
  font-weight: bold;
  font-size: 1.08rem;
  text-decoration: none;
  border: 1px solid #e74c3c;
  border-radius: 6px;
  padding: 4px 14px;
  background: #fff5f5;
  transition: background 0.2s, color 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 10px;
}
.feedback-link:first-child { margin-left: 0; }
.feedback-link:hover {
  background: #e74c3c;
  color: #fff;
}
.dark-theme .feedback-link {
  background: #2d2d2d;
  color: #ff7675;
  border-color: #ff7675;
}
.dark-theme .feedback-link:hover {
  background: #ff7675;
  color: #232323;
}
.feedback-icon {
  font-size: 1.15em;
  color: #e74c3c;
  margin-right: 2px;
}
.feedback-link:hover .feedback-icon {
  color: #fff;
}
.feedback-link:nth-child(2) .feedback-icon { color: #333; }
.feedback-link:nth-child(3) .feedback-icon { color: #f39c12; }
.dark-theme .feedback-icon {
  color: #ff7675;
}
.dark-theme .feedback-link:nth-child(2) .feedback-icon { color: #fff; }
.dark-theme .feedback-link:nth-child(3) .feedback-icon { color: #f7ca77; }

/* 移动端上下布局 */
.mobile-tree-layout {
  display: flex;
  flex-direction: column;
  height: auto;
  min-height: 500px;
}
.mobile-tree-top {
  border-bottom: 1px solid #eaeaea;
  max-height: 50vh;
  overflow-y: auto;
}
.dark-theme .mobile-tree-top {
  border-bottom-color: #404040;
}
.mobile-tree-bottom {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  min-height: 180px;
}
.mobile-batch-trigger {
  margin-top: 12px;
}
.mobile-batch-footer {
  width: 100%;
  border-top: 1px solid #eaeaea;
  padding-top: 10px;
  margin-top: 12px;
}
.dark-theme .mobile-batch-footer {
  border-top-color: #404040;
}

@media (max-width: 768px) {
  .file-grid {
    grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
    gap: 8px;
    padding: 6px;
  }
  .file-item {
    padding: 6px 2px;
    min-height: 60px;
  }
  .file-icon {
    font-size: 1.5rem;
  }
}

@media (max-width: 480px) {
  .file-grid {
    grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
    gap: 4px;
  }
  .action-bar {
    flex-direction: column;
    gap: 6px;
  }
}
</style>
