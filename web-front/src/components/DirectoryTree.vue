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
            :class="getFileTypeClass(file)"
            @click="handleFileClick(file)"
          >
            <div class="file-icon">
              <i :class="getFileIcon(file)"></i>
            </div>
            <div class="file-name">{{ file.fileName }}</div>
            <div class="file-meta">
              {{ file.sizeStr || '0B' }} · {{ formatDate(file.createTime) }}
            </div>
          </div>
          <div v-if="!loading && (!currentFileList || currentFileList.length === 0)" class="empty-state">
            <i class="fas fa-folder-open"></i>
            <h3>此文件夹为空</h3>
            <p>暂无文件或文件夹</p>
          </div>
        </div>
        <div class="action-bar">
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
          </div>
        </div>
      </template>
      <template v-else-if="viewMode === 'tree'">
        <div class="content-card">
          <splitpanes class="split-theme custom-splitpanes" style="height:100%;">
            <pane>
              <div class="tree-sidebar">
                <el-tree
                  :data="treeData"
                  :props="treeProps"
                  node-key="id"
                  lazy
                  :load="loadNode"
                  highlight-current
                  @node-click="onNodeClick"
                  :default-expand-all="false"
                  :default-expanded-keys="['root']"
                  :render-content="renderContent"
                  style="background:transparent;"
                />
              </div>
            </pane>
            <pane>
              <div class="tree-content">
                <div v-if="selectedNode">
                  <div class="file-detail-icon-wrap">
                    <i :class="getFileIcon(selectedNode)" class="file-detail-icon"></i>
                  </div>
                  <h4>{{ selectedNode.fileName }}</h4>
                  <div v-if="selectedNode.fileType === 'folder'">
                    <ul>
                      <li v-for="file in selectedNode.children || []" :key="file.id">
                        <i :class="getFileIcon(file)"></i> {{ file.fileName }}
                      </li>
                    </ul>
                  </div>
                  <div v-else>
                    <p>类型: {{ getFileTypeClass(selectedNode) }}</p>
                    <p>大小: {{ selectedNode.sizeStr || '0B' }}</p>
                    <p>创建时间: {{ formatDate(selectedNode.createTime) }}</p>
                    <!-- 文件详情区下载按钮 -->
                    <el-button v-if="selectedNode && selectedNode.parserUrl" @click="previewFile(selectedNode)">打开</el-button>
                    <a
                      v-if="selectedNode && selectedNode.parserUrl"
                      :href="selectedNode.parserUrl"
                      download
                      target="_blank"
                      class="el-button el-button--success"
                      style="margin-left: 8px;"
                    >
                      下载
                    </a>
                  </div>
                </div>
                <div v-else style="color: #888;">请选择左侧文件或文件夹</div>
              </div>
            </pane>
          </splitpanes>
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
          <a
            v-if="selectedFile && selectedFile.parserUrl"
            :href="selectedFile.parserUrl"
            download
            target="_blank"
            class="el-button el-button--success"
            style="margin-left: 8px;"
          >
            下载
          </a>
        </span>
      </el-dialog>
      <div v-if="isPreviewing" class="preview-mask">
        <div class="preview-toolbar">
          <el-button size="small" @click="closePreview">关闭预览</el-button>
          <el-button size="small" type="primary" @click="openPreviewInNewTab">新窗口打开</el-button>
        </div>
        <iframe :src="previewUrl" frameborder="0" class="preview-iframe"></iframe>
      </div>
    </div>
  </div>
</template>

<script>
import axios from 'axios'
import { ElTree } from 'element-plus'
import { Splitpanes, Pane } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import fileTypeUtils from '@/utils/fileTypeUtils'

export default {
  name: 'DirectoryTree',
  components: { ElTree, Splitpanes, Pane },
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
      treeProps: {
        label: 'fileName',
        children: 'children',
        isLeaf: 'isLeaf'
      }
    }
  },
  computed: {
    folderCount() {
      return this.currentFileList.filter(file => file.fileType === 'folder').length
    },
    fileCount() {
      return this.currentFileList.filter(file => file.fileType !== 'folder').length
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
    // 构建API URL
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
        // 根节点
        resolve(this.treeData[0].children)
      } else if (node.data.fileType === 'folder' && node.data.parserUrl) {
        axios.get(node.data.parserUrl).then(res => {
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
        const response = await axios.get(folder.parserUrl)
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
        const response = await axios.get(currentDir.url)
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
        this.previewUrl = file.previewUrl || file.parserUrl
        this.isPreviewing = true
      } else {
        this.$message.warning('该文件暂无预览链接')
      }
      this.closeFileDialog()
    },
    // 下载文件
    downloadFile(file) {
      if (file?.parserUrl) {
        const iframe = document.createElement('iframe')
        iframe.style.display = 'none'
        iframe.src = file.parserUrl
        document.body.appendChild(iframe)
        setTimeout(() => {
          document.body.removeChild(iframe)
        }, 1000)
        this.$message.success('开始下载文件')
      } else {
        this.$message.warning('该文件暂无下载链接')
      }
      this.closeFileDialog()
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
        window.open(this.previewUrl, '_blank')
      }
    },
    formatDate(timestamp) {
      if (!timestamp) return '未知时间'
      const date = new Date(timestamp)
      return date.toLocaleString('zh-CN')
    },
    checkTheme() {
      const html = document.documentElement;
      const body = document.body;
      if (html && body && html.classList && body.classList) {
        this.isDarkTheme = body.classList.contains('dark-theme') || 
                           html.classList.contains('dark-theme')
      } else {
        this.isDarkTheme = false;
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
    this.initialized = true
    
    // 监听主题变化
    this._observer = new MutationObserver(() => {
      this.checkTheme()
    })
    this._observer.observe(document.body, {
      attributes: true,
      attributeFilter: ['class']
    })
  },
  beforeUnmount() {
    if (this._observer) {
      this._observer.disconnect()
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
}

.directory-tree.dark-theme {
  background: #2d2d2d;
  color: #ffffff;
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
.tree-sidebar {
  width: 220px;
  background: #f8f9fa;
  border-right: 1px solid #eaeaea;
  overflow-y: auto;
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
}
.dark-theme .content-card {
  background: #232323;
  box-shadow: 0 2px 8px rgba(0,0,0,0.18);
}

.split-theme {
  flex: 1 1 0;
  height: 100%;
}

.tree-sidebar, .tree-content {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.tree-content {
  padding: 40px 16px 16px 16px;
  align-items: flex-start;
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
.custom-splitpanes .splitpanes__splitter:hover {
  background: #b3b3b3;
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