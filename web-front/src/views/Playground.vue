<template>
  <div ref="playgroundContainer" class="playground-container" :class="{ 'dark-theme': isDarkMode, 'fullscreen': isFullscreen, 'is-mobile': isMobile }">
    <!-- 加载动画 + 进度条 -->
    <div v-if="loading" class="playground-loading-overlay">
      <div class="playground-loading-card">
        <div class="loading-icon">
          <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        </div>
        <div class="loading-text">正在加载编辑器和编译器...</div>
        <div class="loading-bar">
          <div class="loading-bar-inner" :style="{ width: loadProgress + '%' }"></div>
        </div>
        <div class="loading-percent">{{ loadProgress }}%</div>
        <div class="loading-details">{{ loadingMessage }}</div>
      </div>
    </div>

    <!-- 密码验证界面 -->
    <div v-if="!loading && authChecking" class="playground-auth-loading">
      <el-icon class="is-loading" :size="30"><Loading /></el-icon>
      <span style="margin-left: 10px;">正在检查访问权限...</span>
    </div>

    <!-- 演练场禁用提示 -->
    <div v-if="!loading && !authChecking && !playgroundEnabled" class="playground-auth-overlay">
      <div class="playground-auth-card">
        <div class="auth-icon" style="color: #f56c6c;">
          <el-icon :size="50"><WarningFilled /></el-icon>
        </div>
        <div class="auth-title">演练场功能已禁用</div>
        <div class="auth-subtitle">请联系管理员启用演练场功能</div>
        <el-button type="primary" size="large" @click="goHome" class="auth-button">
          <span>返回首页</span>
        </el-button>
      </div>
    </div>

    <div v-if="shouldShowAuthUI" class="playground-auth-overlay">
      <div class="playground-auth-card">
        <div class="auth-icon">
          <el-icon :size="50"><Lock /></el-icon>
        </div>
        <div class="auth-title">脚本演练场</div>
        <div class="auth-subtitle">请输入访问密码</div>
        <el-input
          v-model="inputPassword"
          type="password"
          placeholder="请输入访问密码"
          size="large"
          @keyup.enter="submitPassword"
          class="auth-input"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
        <div v-if="authError" class="auth-error">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ authError }}</span>
        </div>
        <el-button type="primary" size="large" @click="submitPassword" :loading="authLoading" class="auth-button">
          <el-icon v-if="!authLoading"><Unlock /></el-icon>
          <span>确认登录</span>
        </el-button>
      </div>
    </div>

    <!-- 面包屑导航 - 移到页面最顶部 -->
    <div v-if="authed && !loading" class="breadcrumb-top-bar">
      <el-breadcrumb separator="/" class="breadcrumb-nav">
        <el-breadcrumb-item>
          <el-link :underline="false" @click="goHomeInNewWindow" class="breadcrumb-link">
            <el-icon><HomeFilled /></el-icon>
            <span style="margin-left: 4px;">首页</span>
          </el-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item>
          脚本演练场 
          <span style="color: var(--el-text-color-secondary); font-size: 12px; margin-left: 8px;">
            {{ currentFileLanguageDisplay }}
          </span>
          <!-- Python LSP 状态指示器 -->
          <el-tag 
            v-if="currentFileLanguageDisplay.includes('Python')"
            :type="pylspConnected ? 'success' : 'info'" 
            size="small" 
            style="margin-left: 8px;"
          >
            <el-icon style="margin-right: 3px;">
              <component :is="pylspConnected ? 'CircleCheck' : 'CircleClose'" />
            </el-icon>
            LSP {{ pylspConnected ? '已连接' : '未连接' }}
          </el-tag>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
          
    <!-- 原有内容 - 只在已认证时显示 -->
    <el-card v-if="authed && !loading" class="playground-card">
      <template #header>
        <div class="card-header">
          <div class="header-actions" :class="{ 'mobile-two-rows': isMobile }">
            <!-- 第一排：主要操作（带文字） -->
            <div class="action-row">
              <el-button-group size="small">
                <el-tooltip content="新建文件 (Ctrl+N)" placement="bottom">
                  <el-button icon="DocumentAdd" @click="showNewFileDialog">新建</el-button>
                </el-tooltip>
                <el-tooltip content="保存代码 (Ctrl+S)" placement="bottom">
                  <el-button icon="Document" @click="saveCode">保存</el-button>
                </el-tooltip>
                <el-tooltip content="复制 (Ctrl+C)" placement="bottom">
                  <el-button icon="CopyDocument" @click="copyAll">复制</el-button>
                </el-tooltip>
                <el-tooltip content="粘贴 (Ctrl+V)" placement="bottom">
                  <el-button icon="Notebook" @click="pasteCode">粘贴</el-button>
                </el-tooltip>
                <el-tooltip content="全选 (Ctrl+A)" placement="bottom">
                  <el-button icon="Tickets" @click="selectAll">全选</el-button>
                </el-tooltip>
              </el-button-group>
            </div>
            
            <!-- 第二排：运行和格式化 + 设置 -->
            <div class="action-row">
              <el-button-group size="small">
                <el-tooltip content="运行测试 (Ctrl+Enter)" placement="bottom">
                  <el-button :icon="testing ? 'Loading' : 'CaretRight'" @click="isMobile ? (mobileTestDialogVisible = true) : executeTest()" :loading="testing">
                    运行
                  </el-button>
                </el-tooltip>
                <el-tooltip content="格式化代码 (Shift+Alt+F)" placement="bottom">
                  <el-button icon="MagicStick" @click="formatCode">格式化</el-button>
                </el-tooltip>
              </el-button-group>
              
              <!-- 主题切换 -->
              <el-dropdown size="small" @command="changeTheme" style="margin-left: 10px;">
                <el-button size="small">
                  <el-icon><component :is="themes.find(t => t.name === currentTheme)?.icon || 'Sunny'" /></el-icon>
                  <span style="margin-left: 5px;">{{ currentTheme }}</span>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="theme in themes" :key="theme.name" :command="theme.name">
                      <el-icon><component :is="theme.icon" /></el-icon>
                      <span style="margin-left: 5px;">{{ theme.name }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              
              <!-- 全屏 -->
              <el-tooltip content="全屏模式 (F11)" placement="bottom">
                <el-button size="small" :icon="isFullscreen ? 'FullScreen' : 'FullScreen'" @click="toggleFullscreen" />
              </el-tooltip>
              
              <!-- 更多操作（包含发布脚本等） -->
              <el-dropdown size="small" style="margin-left: 5px;">
                <el-button size="small" icon="More" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item icon="Promotion" @click="publishParser">发布脚本</el-dropdown-item>
                    <el-dropdown-item icon="DocumentAdd" @click="loadTemplate">加载示例 (Ctrl+R)</el-dropdown-item>
                    <el-dropdown-item icon="Upload" @click="importFile">导入文件</el-dropdown-item>
                    <el-dropdown-item icon="Delete" @click="clearCode">清空代码</el-dropdown-item>
                    <el-dropdown-item icon="Download" @click="exportCurrentFile">导出当前JS</el-dropdown-item>
                    <el-dropdown-item :icon="useNativeEditor ? 'Monitor' : 'EditPen'" @click="toggleNativeEditor">
                      {{ useNativeEditor ? '切换Monaco编辑器' : '切换原生编辑器' }}
                    </el-dropdown-item>
                    <el-dropdown-item icon="QuestionFilled" @click="showShortcutsHelp">快捷键 (Ctrl+/)</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              
              <!-- 隐藏的文件导入input -->
              <input 
                ref="fileImportInput"
                type="file"
                style="display: none"
                @change="handleFileImport"
                accept=".js,.py,.txt"
              />
            </div>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 代码编辑标签页 -->
        <el-tab-pane label="代码编辑" name="editor">
          <!-- 文件标签页 -->
          <div class="file-tabs-container">
            <div class="file-tabs-wrapper">
              <el-tabs 
                v-model="activeFileId" 
                type="card" 
                closable 
                @tab-remove="removeFile"
                @tab-change="handleFileChange"
                class="file-tabs"
              >
                <el-tab-pane
                  v-for="file in files"
                  :key="file.id"
                  :label="getFileTabLabel(file)"
                  :name="file.id"
                  :closable="!file.pinned"
                >
                  <template #label>
                    <span 
                      @contextmenu.prevent="showTabContextMenu($event, file)"
                      class="tab-label"
                      :class="{ 'tab-pinned': file.pinned }"
                    >
                      <el-icon v-if="file.pinned" class="pin-icon"><Star /></el-icon>
                      {{ file.name }}{{ file.modified ? ' *' : '' }}
                    </span>
                  </template>
                </el-tab-pane>
              </el-tabs>
              <el-tooltip content="新建文件" placement="bottom">
                <el-button 
                  icon="Plus" 
                  size="small" 
                  circle
                  @click="showNewFileDialog"
                  class="new-file-tab-btn"
                />
              </el-tooltip>
            </div>
          </div>
          
          <!-- 标签页右键菜单 -->
          <div 
            v-if="tabContextMenu.visible" 
            class="tab-context-menu"
            :style="{ left: tabContextMenu.x + 'px', top: tabContextMenu.y + 'px' }"
            @click.stop
          >
            <div class="context-menu-item" @click="contextMenuAction('pin')">
              <el-icon><component :is="tabContextMenu.file?.pinned ? 'StarFilled' : 'Star'" /></el-icon>
              <span>{{ tabContextMenu.file?.pinned ? '取消固定' : '固定' }}</span>
            </div>
            <div class="context-menu-divider"></div>
            <div class="context-menu-item" @click="contextMenuAction('duplicate')">
              <el-icon><CopyDocument /></el-icon>
              <span>复制为新脚本</span>
            </div>
            <div class="context-menu-item" @click="contextMenuAction('export')">
              <el-icon><Download /></el-icon>
              <span>导出文件</span>
            </div>
            <div class="context-menu-divider"></div>
            <div class="context-menu-item" @click="contextMenuAction('closeOthers')" :class="{ disabled: files.length <= 1 }">
              <el-icon><Close /></el-icon>
              <span>关闭其他</span>
            </div>
            <div class="context-menu-item" @click="contextMenuAction('closeRight')" :class="{ disabled: isLastFile(tabContextMenu.file) }">
              <el-icon><Right /></el-icon>
              <span>关闭右侧</span>
            </div>
            <div class="context-menu-item" @click="contextMenuAction('closeAll')" :class="{ disabled: files.length <= 1 }">
              <el-icon><CircleClose /></el-icon>
              <span>关闭全部</span>
            </div>
          </div>
          
          <!-- 移动端：不使用 splitpanes，内容自然向下流动 -->
          <div v-if="isMobile" class="mobile-layout">
            <!-- 编辑器区域 -->
            <div class="editor-section" style="position: relative;" :style="{ height: mobileEditorHeight + 'px' }">
              <!-- 原生编辑器 -->
              <textarea
                v-if="useNativeEditor"
                ref="nativeEditorRef"
                v-model="currentCode"
                class="native-editor"
                :style="{ height: mobileEditorHeight + 'px' }"
                @blur="handleNativeEditorChange"
                spellcheck="false"
                placeholder="在此输入代码..."
              />
              <!-- Monaco 编辑器 -->
              <MonacoEditor
                v-else
                :key="activeFileId"
                ref="editorRef"
                v-model="currentCode"
                :language="currentEditorLanguage"
                :theme="editorTheme"
                :height="mobileEditorHeight + 'px'"
                :options="editorOptions"
                @change="onCodeChange"
              />
              
              <!-- 拖拽条改变编辑器高度 -->
              <div 
                class="editor-resize-handle"
                @mousedown="startResize"
                @touchstart="startResize"
              >
                <div class="resize-handle-bar"></div>
              </div>
              
              <!-- 移动端代码问题浮窗按钮 -->
              <transition name="fade">
                <div v-if="codeProblems.length > 0" class="mobile-problems-btn" @click="showProblemsDialog">
                  <el-badge :value="codeProblems.length" :max="99" class="problems-badge">
                    <el-icon :size="20"><WarningFilled /></el-icon>
                  </el-badge>
                </div>
              </transition>
            </div>
            
            <!-- 移动端当前行问题提示 -->
            <transition name="slide-up">
              <div 
                v-if="isMobile && currentLineProblem" 
                class="mobile-current-problem"
                :class="{ 'warning': currentLineProblem.severity !== 8 }"
              >
                <div class="problem-header">
                  <el-icon :class="currentLineProblem.severity === 8 ? 'error-icon' : 'warning-icon'">
                    <WarningFilled />
                  </el-icon>
                  <span class="problem-title">
                    {{ currentLineProblem.severity === 8 ? '错误' : '警告' }}
                    - 第 {{ currentLineProblem.startLineNumber }} 行
                  </span>
                  <el-icon class="close-icon" @click="currentLineProblem = null">
                    <Close />
                  </el-icon>
                </div>
                <div class="problem-message">
                  {{ currentLineProblem.message }}
                </div>
              </div>
            </transition>
            
            <!-- 测试参数和结果区域 - 移动端不显示，改用弹框 -->
            <div v-if="false" class="test-section mobile-test-section">
              <!-- 测试参数 -->
              <el-card class="test-params-card collapsible-card" shadow="never" style="margin-top: 12px">
                <template #header>
                  <div class="card-header-with-collapse">
                    <span>测试参数</span>
                    <el-button 
                      text 
                      size="small" 
                      :icon="collapsedPanels.testParams ? 'ArrowDown' : 'ArrowUp'"
                      @click="togglePanel('testParams')"
                    />
                  </div>
                </template>
                <transition name="collapse">
                  <div v-show="!collapsedPanels.testParams">
                    <el-form :model="testParams" label-width="0px" size="small" class="test-params-form mobile-single-row">
                      <div class="test-params-row">
                        <el-input
                          v-model="testParams.shareUrl"
                          placeholder="分享链接"
                          clearable
                          size="small"
                          class="url-input"
                        />
                        <el-input
                          v-model="testParams.pwd"
                          placeholder="密码"
                          clearable
                          size="small"
                          class="pwd-input"
                        />
                      </div>
                      <div class="test-params-row">
                        <el-radio-group v-model="testParams.method" size="small" class="method-radio">
                          <el-radio label="parse">parse</el-radio>
                          <el-radio label="parseFileList">list</el-radio>
                        </el-radio-group>
                        <el-button
                          type="primary"
                          :loading="testing"
                          @click="executeTest"
                          size="small"
                          class="test-button"
                        >
                          执行测试
                        </el-button>
                      </div>
                    </el-form>
                  </div>
                </transition>
              </el-card>

              <!-- 执行结果 -->
              <el-card class="result-card collapsible-card" shadow="never" style="margin-top: 10px">
                <template #header>
                  <div class="card-header-with-collapse">
                    <span>执行结果</span>
                    <el-button 
                      text 
                      size="small" 
                      :icon="collapsedPanels.testResult ? 'ArrowDown' : 'ArrowUp'"
                      @click="togglePanel('testResult')"
                    />
                  </div>
                </template>
                <transition name="collapse">
                  <div v-show="!collapsedPanels.testResult">
                    <div v-if="testResult" class="result-content">
                      <el-alert
                        :type="testResult.success ? 'success' : 'error'"
                        :title="testResult.success ? '执行成功' : '执行失败'"
                        :closable="false"
                        style="margin-bottom: 10px"
                      />
                      
                      <div v-if="testResult.success" class="result-section">
                        <div class="section-title">结果数据：</div>
                        <div v-if="testResult.result" class="result-debug-box">
                          <strong>结果内容：</strong>{{ testResult.result }}
                        </div>
                        <JsonViewer :value="testResult.result" :expand-depth="3" />
                      </div>

                      <div v-if="testResult.error" class="result-section">
                        <div class="section-title">错误信息：</div>
                        <el-alert type="error" :title="testResult.error" :closable="false" />
                        <div v-if="testResult.stackTrace" class="stack-trace">
                          <el-collapse>
                            <el-collapse-item title="查看堆栈信息" name="stack">
                              <pre>{{ testResult.stackTrace }}</pre>
                            </el-collapse-item>
                          </el-collapse>
                        </div>
                      </div>

                      <div v-if="testResult.executionTime" class="result-section">
                        <div class="section-title">执行时间：</div>
                        <div>{{ testResult.executionTime }}ms</div>
                      </div>
                    </div>
                    <div v-else class="empty-result">
                      <el-empty description="暂无执行结果" :image-size="80" />
                    </div>
                  </div>
                </transition>
              </el-card>
            </div>
          </div>

          <!-- 桌面端：使用 splitpanes -->
          <Splitpanes v-else class="default-theme" @resized="handleResize">
            <!-- 编辑器区域 (左侧) -->
            <Pane :size="collapsedPanels.rightPanel ? 100 : splitSizes[0]" min-size="30" class="editor-pane">
              <div class="editor-section">
                <!-- 原生编辑器 -->
                <textarea
                  v-if="useNativeEditor"
                  ref="nativeEditorRef"
                  v-model="currentCode"
                  class="native-editor"
                  @blur="handleNativeEditorChange"
                  spellcheck="false"
                  placeholder="在此输入代码..."
                />
                <!-- Monaco 编辑器 -->
                <MonacoEditor
                  v-else
                  :key="activeFileId"
                  ref="editorRef"
                  v-model="currentCode"
                  :language="currentEditorLanguage"
                  :theme="editorTheme"
                  :height="'100%'"
                  :options="editorOptions"
                  @change="onCodeChange"
                />
              </div>
            </Pane>

            <!-- 测试参数和结果区域 (右侧) -->
            <Pane v-if="!collapsedPanels.rightPanel" 
              :size="splitSizes[1]" min-size="20" class="test-pane" style="margin-left: 10px;">
              <div class="test-section">
                <!-- 优化的折叠按钮 -->
                <el-tooltip content="折叠测试面板" placement="left">
                  <div class="panel-collapse-btn" @click="toggleRightPanel">
                    <el-icon><CaretRight /></el-icon>
                  </div>
                </el-tooltip>
                
                <!-- 使用TestPanel组件 -->
                <TestPanel
                  :test-params="testParams"
                  :test-result="testResult"
                  :testing="testing"
                  :code-problems="codeProblems"
                  :url-history="urlHistory"
                  @execute-test="executeTest"
                  @clear-result="testResult = null"
                  @goto-problem="goToProblemLine"
                  @update:test-params="(params) => Object.assign(testParams, params)"
                />
              </div>
            </Pane>
          </Splitpanes>
          
          <!-- 优化的右侧面板展开按钮（当折叠时显示） -->
          <el-tooltip v-if="collapsedPanels.rightPanel" content="展开测试面板" placement="left">
            <div class="panel-expand-btn" @click="toggleRightPanel">
              <el-icon size="20"><CaretLeft /></el-icon>
            </div>
          </el-tooltip>

      <!-- 日志控制台（可折叠） -->
      <transition name="slide-up">
        <el-card v-show="!collapsedPanels.console" class="console-card collapsible-card" shadow="never" style="margin-top: 12px">
          <template #header>
            <div class="card-header-with-collapse">
              <div style="display: flex; align-items: center; gap: 10px;">
                <span>控制台日志</span>
                <el-tag size="small" type="info">{{ consoleLogs.length }}</el-tag>
              </div>
              <div style="display: flex; align-items: center; gap: 5px;">
                <el-tooltip content="清空控制台 (Ctrl+L)" placement="top">
                  <el-button size="small" text icon="Delete" @click="clearConsoleLogs">清空</el-button>
                </el-tooltip>
                <el-tooltip content="折叠控制台" placement="top">
                  <el-button 
                    text 
                    size="small" 
                    icon="ArrowDown"
                    @click="togglePanel('console')"
                  />
                </el-tooltip>
              </div>
            </div>
          </template>
          <div class="console-container">
            <div
              v-for="(log, index) in consoleLogs"
              :key="index"
              :class="[
                'console-entry', 
                'console-' + log.level.toLowerCase(),
                log.source === 'JS' ? 'console-js-source' : (log.source === 'java' ? 'console-java-source' : 'console-python-source')
              ]"
            >
              <span class="console-time">{{ formatTime(log.timestamp) }}</span>
              <span class="console-level">{{ log.level }}</span>
              <span v-if="log.source" class="console-source-tag" :class="'console-source-' + (log.source || 'unknown')">
                [{{ log.source === 'java' ? 'JAVA' : (log.source === 'JS' ? 'JS' : 'PYTHON') }}]
              </span>
              <span class="console-message">{{ log.message }}</span>
            </div>
            <div v-if="consoleLogs.length === 0" class="empty-console">
              <span>暂无日志</span>
            </div>
          </div>
        </el-card>
      </transition>
      
      <!-- 控制台展开按钮（当折叠时显示） -->
      <transition name="fade">
        <div v-if="collapsedPanels.console" class="console-expand-btn" @click="togglePanel('console')">
          <el-icon size="16"><Top /></el-icon>
          <span style="margin-left: 5px;">控制台 ({{ consoleLogs.length }})</span>
        </div>
      </transition>

          <!-- 使用说明（可折叠） -->
          <el-card class="help-card collapsible-card" shadow="never" style="margin-top: 12px">
            <template #header>
              <div class="card-header-with-collapse">
                <span>📖 使用说明</span>
                <el-button 
                  text 
                  size="small" 
                  :icon="collapsedPanels.help ? 'ArrowDown' : 'ArrowUp'"
                  @click="togglePanel('help')"
                />
              </div>
            </template>
            <transition name="collapse">
              <div v-show="!collapsedPanels.help">
              <div class="help-content">
                <h3>什么是脚本演练场？</h3>
                <p>演练场允许您快速编写、测试和发布JavaScript解析脚本，无需重启服务器即可调试和验证解析逻辑。</p>
                
                <h3>快速开始</h3>
                <ol>
                  <li>点击"加载示例"查看示例代码模板</li>
                  <li>修改代码中的解析逻辑</li>
                  <li>输入测试URL和密码，点击"执行测试"验证代码</li>
                  <li>测试通过后，点击"发布脚本"保存到数据库</li>
                </ol>

                <h3>📱 移动端操作说明</h3>
                <ul>
                  <li><strong>顶部运行按钮</strong>：点击打开测试参数弹框，可输入分享链接和密码后执行</li>
                  <li><strong>底部悬浮运行按钮</strong>：
                    <ul style="margin-top: 5px;">
                      <li>点击：使用当前参数直接快速执行测试</li>
                      <li>长按（0.5秒）：打开测试参数弹框，可修改参数</li>
                    </ul>
                  </li>
                  <li><strong>底部悬浮按钮</strong>：从左到右依次为撤销、重做、格式化、全选、运行测试</li>
                  <li><strong>编辑器高度调整</strong>：拖动编辑器底部的横条可调整编辑器高度</li>
                </ul>

                <h3>脚本格式要求</h3>
                <ul>
                  <li>必须包含元数据注释块：
                    <ul style="margin-top: 5px;">
                      <li>JavaScript: <code>// ==UserScript== ... // ==/UserScript==</code></li>
                      <li>Python: <code># ==UserScript== ... # ==/UserScript==</code></li>
                    </ul>
                  </li>
                  <li>必填元数据：<code>@name</code>、<code>@type</code>、<code>@displayName</code>、<code>@match</code></li>
                  <li><code>@type</code> 必须唯一，不能与现有解析器冲突</li>
                  <li><code>@match</code> 必须包含命名捕获组 <code>(?&lt;KEY&gt;...)</code></li>
                  <li>必须实现 <code>parse</code> 函数（必填）</li>
                  <li>可选实现 <code>parseFileList</code> 和 <code>parseById</code> 函数</li>
                </ul>

                <h3>API参考</h3>
                <ul>
                  <li><code>shareLinkInfo</code> - 分享链接信息对象，提供 <code>getShareUrl()</code>、<code>getShareKey()</code> 等方法</li>
                  <li><code>http</code> - HTTP客户端，提供 <code>get()</code>、<code>post()</code>、<code>sendJson()</code> 等方法</li>
                  <li><code>logger</code> - 日志对象，提供 <code>info()</code>、<code>debug()</code>、<code>error()</code> 等方法</li>
                </ul>

                <h3>发布脚本</h3>
                <ul>
                  <li>脚本会保存到数据库，最多可创建100个解析器</li>
                  <li>发布的解析器可以在"解析器列表"标签页中查看和管理</li>
                  <li>可以编辑、删除已发布的解析器</li>
                  <li>发布成功后会显示API调用示例，包含302重定向和JSON响应两种方式</li>
                </ul>

                <h3>📡 API调用方式</h3>
                <p>发布解析器后，可以通过以下API端点调用：</p>
                
                <h4>1. 302重定向（直接下载）</h4>
                <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto;">GET /parser?url=分享链接&pwd=密码</pre>
                <p style="color: #666; font-size: 13px;">返回302重定向到下载地址，浏览器会自动跳转下载</p>
                
                <h4>2. JSON响应（获取解析结果）</h4>
                <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto;">GET /json/parser?url=分享链接&pwd=密码</pre>
                <p style="color: #666; font-size: 13px;">返回JSON格式的解析结果，包含下载链接等详细信息</p>
                
                <h4>使用示例</h4>
                <div style="background: #f5f5f5; padding: 10px; border-radius: 4px; margin: 10px 0;">
                  <p><strong>浏览器访问：</strong></p>
                  <code>http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd</code>
                </div>
                
                <div style="background: #f5f5f5; padding: 10px; border-radius: 4px; margin: 10px 0;">
                  <p><strong>curl命令：</strong></p>
                  <code>curl "http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd"</code>
                </div>
                
                <div style="background: #f5f5f5; padding: 10px; border-radius: 4px; margin: 10px 0;">
                  <p><strong>JavaScript调用：</strong></p>
                  <code>fetch('/json/parser?url=' + encodeURIComponent(shareUrl))<br>
                  &nbsp;&nbsp;.then(res => res.json())<br>
                  &nbsp;&nbsp;.then(data => console.log(data.data.url))</code>
                </div>
                
                <p style="color: #e6a23c; margin-top: 10px;">
                  💡 <strong>提示：</strong>发布成功后会自动显示完整的API调用示例
                </p>

                <h3>注意事项</h3>
                <ul>
                  <li>演练场脚本与正式解析器隔离，不会影响现有解析器规则</li>
                  <li>所有HTTP请求都是同步的，不支持异步操作</li>
                  <li>仅支持ES5.1语法（Nashorn引擎限制）</li>
                  <li>建议在发布前充分测试脚本的正确性</li>
                </ul>

                <h3>📖 参考文档</h3>
                <p>更多详细信息，请参考 GitHub 仓库文档：</p>
                <ul>
                  <li>
                    <a href="https://github.com/qaiu/netdisk-fast-download/blob/main/parser/doc/JAVASCRIPT_PARSER_GUIDE.md" target="_blank" rel="noopener noreferrer">
                      JavaScript 解析器开发指南
                    </a>
                  </li>
                  <li>
                    <a href="https://github.com/qaiu/netdisk-fast-download/blob/main/parser/doc/CUSTOM_PARSER_GUIDE.md" target="_blank" rel="noopener noreferrer">
                      自定义解析器扩展指南
                    </a>
                  </li>
                  <li>
                    <a href="https://github.com/qaiu/netdisk-fast-download/blob/main/parser/doc/CUSTOM_PARSER_QUICKSTART.md" target="_blank" rel="noopener noreferrer">
                      快速开始教程
                    </a>
                  </li>
                  <li>
                    <a href="https://github.com/qaiu/netdisk-fast-download/blob/main/parser/README.md" target="_blank" rel="noopener noreferrer">
                      解析器模块文档
                    </a>
                  </li>
                </ul>
              </div>
              </div>
            </transition>
          </el-card>
        </el-tab-pane>

        <!-- 解析器列表标签页 -->
        <el-tab-pane label="解析器列表" name="list">
          <div class="parser-list-section">
            <el-table :data="parserList" v-loading="loadingList" style="width: 100%">
              <el-table-column prop="name" label="名称" width="150" />
              <el-table-column prop="type" label="类型标识" width="120" />
              <el-table-column label="语言" width="80">
                <template #default="scope">
                  <el-tag :type="scope.row.language === 'python' ? 'warning' : 'primary'" size="small">
                    {{ scope.row.language === 'python' ? 'PY' : 'JS' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="displayName" label="显示名称" width="150" />
              <el-table-column prop="author" label="作者" width="100" />
              <el-table-column prop="version" label="版本" width="80" />
              <el-table-column prop="createTime" label="创建时间" width="180">
                <template #default="scope">
                  {{ formatDateTime(scope.row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="enabled" label="状态" width="80">
                <template #default="scope">
                  <el-tag :type="scope.row.enabled ? 'success' : 'info'">
                    {{ scope.row.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="scope">
                  <el-button size="small" @click="loadParserToEditor(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="deleteParser(scope.row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 发布对话框 -->
    <el-dialog 
      v-model="publishDialogVisible" 
      title="发布解析器" 
      :width="isMobile ? '90%' : '600px'"
      :close-on-click-modal="false"
      class="publish-dialog"
    >
      <el-form :model="publishForm" :label-width="isMobile ? '80px' : '100px'">
        <el-form-item label="脚本代码">
          <el-input
            v-model="publishForm.jsCode"
            type="textarea"
            :rows="isMobile ? 8 : 10"
            readonly
            class="publish-code-textarea"
          />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          style="margin-bottom: 20px"
          class="publish-alert"
        >
          <template #title>
            <div class="publish-checklist">
              <p style="margin-bottom: 8px; font-weight: 500;">发布前请确保：</p>
              <ul style="margin: 0; padding-left: 20px;">
                <li>脚本已通过测试</li>
                <li>元数据信息完整（@name, @type, @displayName, @match）</li>
                <li>类型标识（@type）唯一，不与现有解析器冲突</li>
                <li>当前解析器数量未超过100个</li>
              </ul>
            </div>
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <div class="dialog-footer-mobile">
          <el-button @click="publishDialogVisible = false" :size="isMobile ? 'default' : 'default'">取消</el-button>
          <el-button type="primary" :loading="publishing" @click="confirmPublish" :size="isMobile ? 'default' : 'default'">确认发布</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 新建文件对话框 -->
    <el-dialog 
      v-model="newFileDialogVisible" 
      title="新建解析器文件" 
      :width="isMobile ? '90%' : '600px'"
      :close-on-click-modal="false"
      class="new-file-dialog"
    >
      <el-form 
        ref="newFileFormRef"
        :model="newFileForm" 
        :rules="newFileFormRules"
        :label-width="isMobile ? '80px' : '100px'"
      >
        <el-form-item label="开发语言" prop="language">
          <el-radio-group v-model="newFileForm.language" class="language-radio-group">
            <el-radio label="javascript" class="language-radio">
              <span class="language-icon js-icon">JS</span>
              <span class="language-name">JavaScript (ES5)</span>
            </el-radio>
            <el-radio label="python" class="language-radio">
              <span class="language-icon py-icon">🐍</span>
              <span class="language-name">Python (GraalPy)</span>
            </el-radio>
          </el-radio-group>
          <div class="form-tip">选择解析器开发语言</div>
        </el-form-item>
        <el-form-item label="解析器名" prop="name">
          <el-input
            v-model="newFileForm.name"
            placeholder="例如: 示例解析器"
            clearable
          />
          <div class="form-tip">必填, 将作为文件名和@name</div>
        </el-form-item>
        <el-form-item label="标识" prop="identifier">
          <el-input
            v-model="newFileForm.identifier"
            placeholder="例如: example_parser"
            clearable
          />
          <div class="form-tip">必填, 将作为@type类型标识</div>
        </el-form-item>
        <el-form-item label="作者">
          <el-input
            v-model="newFileForm.author"
            placeholder="例如: yourname"
            clearable
          />
          <div class="form-tip">可选, 默认为 yourname</div>
        </el-form-item>
        <el-form-item label="域名匹配">
          <el-input
            v-model="newFileForm.match"
            placeholder="例如: https?://example.com/s/(?&lt;KEY&gt;\w+)"
            clearable
          />
          <div class="form-tip">可选, 正则表达式, 用于匹配分享链接URL</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer-mobile">
          <el-button @click="newFileDialogVisible = false" :size="isMobile ? 'default' : 'default'">取消</el-button>
          <el-button type="primary" @click="createNewFile" :size="isMobile ? 'default' : 'default'">创建</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 代码问题对话框（移动端） -->
    <el-dialog 
      v-model="problemsDialogVisible"
      title="代码问题"
      :width="isMobile ? '90%' : '600px'"
      class="problems-dialog"
    >
      <div v-if="codeProblems.length > 0" class="problems-list">
        <el-alert
          v-for="(problem, index) in codeProblems"
          :key="index"
          :title="`行 ${problem.startLineNumber}: ${problem.message}`"
          :type="problem.severity === 8 ? 'error' : problem.severity === 4 ? 'warning' : 'info'"
          :closable="false"
          style="margin-bottom: 10px; cursor: pointer;"
          @click="goToProblemLine(problem)"
        >
          <template #default>
            <div style="font-size: 12px; color: #666; margin-top: 5px;">
              第 {{problem.startLineNumber}} 行，第 {{problem.startColumn}} 列
            </div>
          </template>
        </el-alert>
      </div>
      <el-empty v-else description="没有发现代码问题" :image-size="80" />
      <template #footer>
        <el-button @click="problemsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    
    <!-- 移动端悬浮操作按钮 - 固定定位 -->
    <div v-if="isMobile" class="mobile-editor-actions">
      <el-button-group size="small">
        <el-tooltip content="撤销" placement="top">
          <el-button 
            icon="RefreshLeft" 
            circle
            @click="undo"
          />
        </el-tooltip>
        <el-tooltip content="重做" placement="top">
          <el-button 
            icon="RefreshRight" 
            circle
            @click="redo"
          />
        </el-tooltip>
        <el-tooltip content="格式化" placement="top">
          <el-button 
            icon="MagicStick" 
            circle
            @click="formatCode"
          />
        </el-tooltip>
        <el-tooltip content="全选" placement="top">
          <el-button 
            icon="Select" 
            circle
            @click="selectAll"
          />
        </el-tooltip>
        <el-tooltip content="运行测试" placement="top">
          <el-button 
            type="primary" 
            icon="CaretRight" 
            circle 
            @click="handleMobileQuickTest"
            @touchstart="handleRunButtonTouchStart"
            @touchend="handleRunButtonTouchEnd"
            @touchcancel="handleRunButtonTouchEnd"
            :loading="testing"
          />
        </el-tooltip>
      </el-button-group>
    </div>
    
    <!-- 移动端测试模态框 -->
    <MobileTestModal
      v-model="mobileTestDialogVisible"
      :test-params="testParams"
      :test-result="testResult"
      :testing="testing"
      :url-history="urlHistory"
      @execute-test="handleMobileExecuteTest"
      @update:test-params="(params) => Object.assign(testParams, params)"
    />
    
    <!-- 快捷键帮助对话框 -->
    <el-dialog 
      v-model="shortcutsDialogVisible" 
      title="⌨️ 快捷键" 
      :width="isMobile ? '90%' : '500px'"
      class="shortcuts-dialog"
    >
      <el-table :data="shortcutsData" style="width: 100%" :show-header="false" class="shortcuts-table">
        <el-table-column prop="name" label="功能" :width="isMobile ? 120 : 200" />
        <el-table-column prop="keys" label="快捷键">
          <template #default="{ row }">
            <el-tag v-for="key in row.keys" :key="key" size="small" style="margin-right: 5px; margin-bottom: 4px;">
              {{ key }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" @click="shortcutsDialogVisible = false" :size="isMobile ? 'default' : 'default'">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useMagicKeys, useFullscreen, useEventListener } from '@vueuse/core';
import { useRouter } from 'vue-router';
import { Splitpanes, Pane } from 'splitpanes';
import 'splitpanes/dist/splitpanes.css';
import MonacoEditor from '@/components/MonacoEditor.vue';
import TestPanel from '@/components/TestPanel.vue';
import MobileTestModal from '@/components/MobileTestModal.vue';
import { playgroundApi } from '@/utils/playgroundApi';
import { configureMonacoTypes, loadTypesFromApi } from '@/utils/monacoTypes';
import PylspClient from '@/utils/pylspClient';
import JsonViewer from 'vue3-json-viewer';
// 导入模板文件
import {
  generateTemplate,
  getEmptyTemplate,
  JS_EMPTY_TEMPLATE
} from '@/templates';

export default {
  name: 'Playground',
  components: {
    MonacoEditor,
    JsonViewer,
    Splitpanes,
    Pane,
    TestPanel,
    MobileTestModal
  },
  setup() {
    const router = useRouter();
    
    // 语言常量
    const LANGUAGE = {
      JAVASCRIPT: 'JavaScript'
    };

    const editorRef = ref(null);
    const fileImportInput = ref(null);
    const jsCode = ref('');
    
    // ===== 编辑器模式切换 =====
    const useNativeEditor = ref(false);
    const nativeEditorRef = ref(null);
    
    // ===== 多文件管理 =====
    const files = ref([
      { id: 'file1', name: '示例解析器.js', content: '', modified: false, pinned: false, dbId: null }
    ]);
    const activeFileId = ref('file1');
    const fileIdCounter = ref(1);
    
    // ===== 标签页右键菜单 =====
    const tabContextMenu = ref({
      visible: false,
      x: 0,
      y: 0,
      file: null
    });
    
    // 显示右键菜单
    const showTabContextMenu = (event, file) => {
      tabContextMenu.value = {
        visible: true,
        x: event.clientX,
        y: event.clientY,
        file: file
      };
    };
    
    // 隐藏右键菜单
    const hideTabContextMenu = () => {
      tabContextMenu.value.visible = false;
    };
    
    // 判断是否是最后一个文件（用于禁用"关闭右侧"）
    const isLastFile = (file) => {
      if (!file) return true;
      const index = files.value.findIndex(f => f.id === file.id);
      return index === files.value.length - 1;
    };
    
    // 获取文件标签显示文本
    const getFileTabLabel = (file) => {
      return file.name + (file.modified ? ' *' : '');
    };
    
    // 右键菜单操作
    const contextMenuAction = (action) => {
      const file = tabContextMenu.value.file;
      if (!file) return;
      
      switch (action) {
        case 'pin':
          file.pinned = !file.pinned;
          // 固定的文件移到最前面
          if (file.pinned) {
            const index = files.value.findIndex(f => f.id === file.id);
            if (index > 0) {
              files.value.splice(index, 1);
              // 找到第一个非固定文件的位置
              const firstUnpinnedIndex = files.value.findIndex(f => !f.pinned);
              if (firstUnpinnedIndex === -1) {
                files.value.push(file);
              } else {
                files.value.splice(firstUnpinnedIndex, 0, file);
              }
            }
          }
          saveAllFilesToStorage();
          break;
          
        case 'duplicate':
          duplicateFile(file);
          break;
          
        case 'export':
          exportFile(file);
          break;
          
        case 'closeOthers':
          closeOtherFiles(file);
          break;
          
        case 'closeRight':
          closeRightFiles(file);
          break;
          
        case 'closeAll':
          closeAllFiles(file);
          break;
      }
      
      hideTabContextMenu();
    };
    
    // 复制为新脚本
    const duplicateFile = (file) => {
      fileIdCounter.value++;
      const ext = file.name.match(/\.(js|py)$/)?.[0] || '.js';
      const baseName = file.name.replace(/\.(js|py)$/, '');
      let newName = `${baseName}_副本${ext}`;
      let counter = 1;
      while (files.value.some(f => f.name === newName)) {
        newName = `${baseName}_副本${counter}${ext}`;
        counter++;
      }
      
      const newFile = {
        id: 'file' + fileIdCounter.value,
        name: newName,
        content: file.content,
        language: file.language,
        modified: true,
        pinned: false,
        dbId: null
      };
      
      files.value.push(newFile);
      activeFileId.value = newFile.id;
      saveAllFilesToStorage();
      ElMessage.success('已复制为新脚本');
    };
    
    // 导出文件
    const exportFile = (file) => {
      const blob = new Blob([file.content], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      ElMessage.success('文件已导出');
    };
    
    // 关闭其他文件
    const closeOtherFiles = (keepFile) => {
      // 保留固定的文件和当前文件
      files.value = files.value.filter(f => f.id === keepFile.id || f.pinned);
      if (!files.value.find(f => f.id === activeFileId.value)) {
        activeFileId.value = keepFile.id;
      }
      saveAllFilesToStorage();
    };
    
    // 关闭右侧文件
    const closeRightFiles = (file) => {
      const index = files.value.findIndex(f => f.id === file.id);
      // 保留固定的文件
      files.value = files.value.filter((f, i) => i <= index || f.pinned);
      if (!files.value.find(f => f.id === activeFileId.value)) {
        activeFileId.value = file.id;
      }
      saveAllFilesToStorage();
    };
    
    // 关闭全部文件（保留一个默认文件）
    const closeAllFiles = (exceptFile) => {
      // 保留固定的文件
      const pinnedFiles = files.value.filter(f => f.pinned);
      if (pinnedFiles.length > 0) {
        files.value = pinnedFiles;
        activeFileId.value = pinnedFiles[0].id;
      } else {
        // 创建一个新的默认文件
        fileIdCounter.value++;
        const newFile = {
          id: 'file' + fileIdCounter.value,
          name: '示例解析器.js',
          content: exampleCode,
          language: 'javascript',
          modified: false,
          pinned: false,
          dbId: null
        };
        files.value = [newFile];
        activeFileId.value = newFile.id;
      }
      saveAllFilesToStorage();
    };
    
    // 获取当前活动文件
    const activeFile = computed(() => {
      return files.value.find(f => f.id === activeFileId.value) || files.value[0];
    });
    
    // 当前文件语言类型（用于显示）
    const currentFileLanguageDisplay = computed(() => {
      const file = activeFile.value;
      if (!file) return 'JavaScript (ES5)';
      
      // 优先使用文件的language属性
      if (file.language === 'python') {
        return 'Python (GraalPy)';
      }
      
      // 根据文件扩展名判断
      if (file.name && file.name.endsWith('.py')) {
        return 'Python (GraalPy)';
      }
      
      return 'JavaScript (ES5)';
    });
    
    // 当前文件的编辑器语言（传递给 MonacoEditor）
    const currentEditorLanguage = computed(() => {
      const file = activeFile.value;
      if (!file) return 'javascript';
      
      // 优先使用文件的language属性
      if (file.language === 'python') {
        return 'python';
      }
      
      // 根据文件扩展名判断
      if (file.name && file.name.endsWith('.py')) {
        return 'python';
      }
      
      return 'javascript';
    });
    
    // 当前编辑的代码（绑定到活动文件）
    const isFileChanging = ref(false); // 标记是否正在切换文件
    const currentCode = computed({
      get: () => activeFile.value?.content || '',
      set: (value) => {
        if (activeFile.value && !isFileChanging.value) {
          // 只有在不是切换文件时才标记为已修改
          const oldContent = activeFile.value.content;
          activeFile.value.content = value;
          // 只有当内容真正改变时才标记为已修改
          if (oldContent !== value) {
            activeFile.value.modified = true;
          }
        }
      }
    });
    
    // ===== 新建文件对话框 =====
    const newFileDialogVisible = ref(false);
    const newFileForm = ref({
      name: '',
      identifier: '',
      author: '',
      match: '',
      language: 'javascript'
    });
    const newFileFormRules = {
      name: [
        { required: true, message: '请输入解析器名称', trigger: 'blur' }
      ],
      identifier: [
        { required: true, message: '请输入标识', trigger: 'blur' }
      ],
      language: [
        { required: true, message: '请选择开发语言', trigger: 'change' }
      ]
    };
    const newFileFormRef = ref(null);
    
    // ===== 加载和认证状态 =====
    const loading = ref(true);
    const loadProgress = ref(0);
    const loadingMessage = ref('初始化...');
    const authChecking = ref(true);
    const authed = ref(false);
    const inputPassword = ref('');
    const authError = ref('');
    const authLoading = ref(false);
    const playgroundEnabled = ref(true); // 演练场是否启用
    
    // ===== 移动端检测 =====
    const isMobile = ref(false);
    
    const testParams = ref({
      shareUrl: 'https://example.com/s/abc',
      pwd: '',
      method: 'parse'
    });
    const testResult = ref(null);
    const testing = ref(false);
    const isDarkMode = ref(false);
    const activeTab = ref('editor');
    const parserList = ref([]);
    const loadingList = ref(false);
    const publishDialogVisible = ref(false);
    const publishing = ref(false);
    const publishForm = ref({
      jsCode: '',
      language: 'javascript'
    });
    const overwriteInfo = ref(null); // 存储需要覆盖的解析器信息
    const helpCollapseActive = ref([]); // 默认折叠
    const consoleLogs = ref([]); // 控制台日志
    
    // ===== Python LSP 客户端 =====
    let pylspClient = null;
    const pylspConnected = ref(false);
    
    // ===== 代码问题 =====
    const codeProblems = ref([]);
    const problemsDialogVisible = ref(false);
    const currentLineProblem = ref(null); // 当前行的问题
    let markersChangeListener = null; // Monaco标记变化监听器
    let cursorChangeListener = null; // 光标变化监听器
    
    // ===== 移动端测试模态框 =====
    const mobileTestDialogVisible = ref(false);
    const mobileResultDialogVisible = ref(false);
    
    // ===== 移动端编辑器高度 =====
    const mobileEditorHeight = ref(350); // 默认高度350px
    let isResizing = ref(false);
    let startY = 0;
    let startHeight = 0;
    
    // 开始拖拽
    const startResize = (e) => {
      isResizing.value = true;
      startY = e.touches ? e.touches[0].clientY : e.clientY;
      startHeight = mobileEditorHeight.value;
      
      document.addEventListener('mousemove', doResize);
      document.addEventListener('mouseup', stopResize);
      document.addEventListener('touchmove', doResize);
      document.addEventListener('touchend', stopResize);
      
      e.preventDefault();
    };
    
    // 拖拽中
    const doResize = (e) => {
      if (!isResizing.value) return;
      
      const currentY = e.touches ? e.touches[0].clientY : e.clientY;
      const diff = currentY - startY;
      const newHeight = Math.max(200, Math.min(window.innerHeight - 150, startHeight + diff));
      mobileEditorHeight.value = newHeight;
    };
    
    // 停止拖拽
    const stopResize = () => {
      isResizing.value = false;
      document.removeEventListener('mousemove', doResize);
      document.removeEventListener('mouseup', stopResize);
      document.removeEventListener('touchmove', doResize);
      document.removeEventListener('touchend', stopResize);
    };
    
    // ===== URL历史记录 =====
    const urlHistory = ref([]);
    const HISTORY_KEY = 'playground_url_history';
    const MAX_HISTORY = 10;
    
    // ===== 右侧Tab页签 =====
    const rightPanelTab = ref('test'); // test, problems
    
    // ===== 新增状态管理 =====
    // 折叠状态
    const collapsedPanels = ref({
      rightPanel: false,      // 右侧整体面板
      testParams: false,      // 测试参数卡片
      testResult: false,      // 测试结果卡片
      codeProblems: false,    // 代码问题卡片
      console: false,         // 控制台卡片
      help: true              // 使用说明（默认折叠）
    });
    
    // 主题状态
    const currentTheme = ref('Light'); // Light, Dark, High Contrast
    const themes = [
      { name: 'Light', editor: 'vs', page: 'light', icon: 'Sunny' },
      { name: 'Dark', editor: 'vs-dark', page: 'dark', icon: 'Moon' },
      { name: 'High Contrast', editor: 'hc-black', page: 'dark', icon: 'MostlyCloudy' }
    ];
    
    // 全屏状态
    const isFullscreen = ref(false);
    const playgroundContainer = ref(null);
    
    // 快捷键帮助弹窗
    const shortcutsDialogVisible = ref(false);
    
    // 快捷键数据
    const shortcutsData = [
      { name: '运行测试', keys: ['Ctrl+Enter', 'Cmd+Enter'] },
      { name: '保存代码', keys: ['Ctrl+S', 'Cmd+S'] },
      { name: '格式化代码', keys: ['Shift+Alt+F'] },
      { name: '全屏模式', keys: ['F11'] },
      { name: '清空控制台', keys: ['Ctrl+L', 'Cmd+L'] },
      { name: '重置代码', keys: ['Ctrl+R', 'Cmd+R'] },
      { name: '编辑器缩放', keys: ['Ctrl+滚轮', 'Cmd+滚轮', 'Ctrl+Plus/Minus', 'Cmd+Plus/Minus'] },
      { name: '快捷键帮助', keys: ['Ctrl+/', 'Cmd+/'] }
    ];
    
    // 分栏大小
    const splitSizes = ref([70, 30]);

    // 示例代码模板 - 使用导入的模板
    const exampleCode = JS_EMPTY_TEMPLATE;

    // 编辑器主题
    const editorTheme = computed(() => {
      // 根据当前主题名称直接判断，而不是依赖 isDarkMode
      const theme = themes.find(t => t.name === currentTheme.value);
      if (theme) {
        return theme.editor;
      }
      // 如果没有找到主题，回退到基于 isDarkMode 的判断
      return isDarkMode.value ? 'vs-dark' : 'vs';
    });
    
    // 计算属性：是否需要显示密码输入界面
    const shouldShowAuthUI = computed(() => {
      return !loading.value && !authChecking.value && !authed.value && playgroundEnabled.value;
    });

    // 编辑器配置
    const wordWrapEnabled = ref(true);
    const editorOptions = computed(() => {
      const baseOptions = {
        minimap: { enabled: !isMobile.value }, // 移动端禁用 minimap
        scrollBeyondLastLine: false,
        wordWrap: wordWrapEnabled.value ? 'on' : 'off',
        lineNumbers: 'on',
        lineNumbersMinChars: isMobile.value ? 3 : 5, // 移动端行号最多显示3位
        // 移动端禁用自动格式化，避免粘贴时每行前面添加额外空格
        formatOnPaste: !isMobile.value,
        formatOnType: !isMobile.value,
        tabSize: 2,
        // 启用缩放功能
        mouseWheelZoom: true, // PC端：Ctrl/Cmd + 鼠标滚轮缩放
        fontSize: 14, // 默认字体大小
        quickSuggestions: true,
        // 移动端支持触摸缩放
        ...(isMobile.value ? {
          // 移动端特殊配置
        } : {})
      };
      return baseOptions;
    });
    
    // ===== 移动端检测 =====
    const updateIsMobile = () => {
      const wasMobile = isMobile.value;
      isMobile.value = window.innerWidth <= 768;
      // 如果是移动端，调整分栏大小，让测试面板有更多空间
      if (isMobile.value && !wasMobile) {
        splitSizes.value = [50, 50]; // 移动端：编辑器50%，测试面板50%
      } else if (!isMobile.value && wasMobile) {
        splitSizes.value = [70, 30]; // 桌面端：编辑器70%，测试面板30%
      }
    };
    
    // ===== 进度设置函数 =====
    const setProgress = (progress, message = '') => {
      if (progress > loadProgress.value) {
        loadProgress.value = progress;
      }
      if (message) {
        loadingMessage.value = message;
      }
    };
    
    // ===== 认证相关函数 =====
    const checkAuthStatus = async () => {
      try {
        const res = await playgroundApi.getStatus();
        if (res.code === 200 && res.data) {
          // 检查是否启用
          playgroundEnabled.value = res.data.enabled === true;
          
          if (!playgroundEnabled.value) {
            authChecking.value = false;
            return false;
          }
          
          // 先检查localStorage中是否有保存的登录信息
          const savedAuth = localStorage.getItem('playground_authed');
          const authTime = localStorage.getItem('playground_auth_time');
          
          // 如果30天内登录过，直接认为已认证（实际认证状态由后端session决定）
          if (savedAuth === 'true' && authTime) {
            const daysSinceAuth = (Date.now() - parseInt(authTime)) / (1000 * 60 * 60 * 24);
            if (daysSinceAuth < 30) {
              // 先设置为已认证，然后验证后端session
              authed.value = true;
            }
          }
          
          const isAuthed = res.data.authed || res.data.public;
          authed.value = isAuthed;
          
          // 如果后端session已失效，清除localStorage
          if (!isAuthed && savedAuth === 'true') {
            localStorage.removeItem('playground_authed');
            localStorage.removeItem('playground_auth_time');
          }
          
          return isAuthed;
        }
        playgroundEnabled.value = false;
        return false;
      } catch (error) {
        console.error('检查认证状态失败:', error);
        // 如果错误信息包含"已禁用"，则设置启用状态为false
        if (error.message && error.message.includes('已禁用')) {
          playgroundEnabled.value = false;
        } else {
          ElMessage.error('检查访问权限失败: ' + error.message);
        }
        return false;
      } finally {
        authChecking.value = false;
      }
    };
    
    // 返回首页
    const goHome = () => {
      router.push('/');
    };
    
    // 新窗口打开首页
    const goHomeInNewWindow = () => {
      window.open('/', '_blank');
    };
    
    // 检查是否有未保存的文件
    const hasUnsavedFiles = computed(() => {
      return files.value.some(f => f.modified);
    });
    
    // 页面关闭/刷新前的提示
    const handleBeforeUnload = (e) => {
      if (hasUnsavedFiles.value) {
        e.preventDefault();
        e.returnValue = '您有未保存的文件，确定要离开吗？';
        return e.returnValue;
      }
    };
    
    const submitPassword = async () => {
      if (!inputPassword.value.trim()) {
        authError.value = '请输入密码';
        return;
      }
      
      authError.value = '';
      authLoading.value = true;
      
      try {
        const res = await playgroundApi.login(inputPassword.value);
        if (res.code === 200 || res.success) {
          authed.value = true;
          // 保存登录信息到localStorage，避免每次都需要登录
          localStorage.setItem('playground_authed', 'true');
          localStorage.setItem('playground_auth_time', Date.now().toString());
          ElMessage.success('登录成功');
          await initPlayground();
        } else {
          authError.value = res.msg || res.message || '密码错误';
        }
      } catch (error) {
        authError.value = error.message || '登录失败，请重试';
      } finally {
        authLoading.value = false;
      }
    };
    
    // ===== Playground 初始化 =====
    const initPlayground = async () => {
      loading.value = true;
      loadProgress.value = 0;
      
      try {
        setProgress(10, '初始化Vue组件...');
        await nextTick();
        
        setProgress(20, '加载配置和本地数据...');
        
        // 加载保存的文件列表
        loadAllFilesFromStorage();
        
        // 如果没有文件，加载默认代码
        if (files.value.length === 0 || !files.value[0].content) {
          const saved = localStorage.getItem('playground_code');
          if (saved) {
            if (files.value.length === 0) {
              files.value.push({ id: 'file1', name: '文件1.js', content: saved, modified: false });
            } else {
              files.value[0].content = saved;
            }
          } else {
            if (files.value.length === 0) {
              files.value.push({ id: 'file1', name: '文件1.js', content: exampleCode, modified: false });
            } else {
              files.value[0].content = exampleCode;
            }
            testParams.value.shareUrl = 'https://example.com/s/abc';
            testParams.value.pwd = '';
            testParams.value.method = 'parse';
          }
        }
        
        // 更新第一个文件的名称（从代码中提取）
        if (files.value.length > 0 && files.value[0].id === 'file1') {
          updateFileNameFromCode(files.value[0]);
        }
        
        // 先加载保存的主题（在编辑器初始化之前）
        const savedTheme = localStorage.getItem('playground_theme');
        if (savedTheme) {
          currentTheme.value = savedTheme;
          const theme = themes.find(t => t.name === savedTheme);
          if (theme) {
            // 同步更新 isDarkMode
            isDarkMode.value = theme.page === 'dark';
            
            await nextTick();
            const html = document.documentElement;
            const body = document.body;
            if (html && body && html.classList && body.classList) {
              if (theme.page === 'dark') {
                html.classList.add('dark');
                body.classList.add('dark-theme');
                body.style.backgroundColor = '#0a0a0a';
              } else {
                html.classList.remove('dark');
                body.classList.remove('dark-theme');
                body.style.backgroundColor = '#f0f2f5';
              }
            }
          }
        }
        
        
        setProgress(50, '初始化Monaco Editor类型定义...');
        await initMonacoTypes();
        
        setProgress(80, '加载完成...');
        
        // 加载保存的折叠状态
        const savedCollapsed = localStorage.getItem('playground_collapsed_panels');
        if (savedCollapsed) {
          try {
            collapsedPanels.value = JSON.parse(savedCollapsed);
          } catch (e) {
            console.warn('加载折叠状态失败', e);
          }
        }
        
        setProgress(100, '初始化完成！');
        await new Promise(resolve => setTimeout(resolve, 300));
        
        // 初始化编辑器后，设置当前文件的语言模式
        await nextTick();
        if (activeFile.value) {
          const language = activeFile.value.language || getLanguageFromFile(activeFile.value.name);
          updateEditorLanguage(language);
        }
        
        // 初始化 Python LSP 客户端（异步，不阻塞主流程）
        initPylspClient().catch(err => {
          console.warn('[Playground] pylsp 初始化失败:', err);
        });
        
      } catch (error) {
        console.error('初始化失败:', error);
        ElMessage.error('初始化失败: ' + error.message);
      } finally {
        loading.value = false;
      }
    };

    // 初始化Monaco Editor类型定义
    const initMonacoTypes = async () => {
      try {
        // 动态导入loader
        const loaderModule = await import('@monaco-editor/loader');
        const loader = loaderModule.default || loaderModule.loader || loaderModule;
        
        if (!loader || typeof loader.init !== 'function') {
          console.error('Monaco Editor loader加载失败');
          return;
        }
        
        // 配置Monaco Editor使用本地打包的文件，而不是CDN
        if (loader.config) {
          const vsPath = process.env.NODE_ENV === 'production' 
            ? './js/vs'  // 生产环境使用相对路径
            : '/js/vs';  // 开发环境使用绝对路径
          
          loader.config({ 
            paths: { 
              vs: vsPath
            }
          });
        }
        
        const monaco = await loader.init();
        if (monaco) {
          await configureMonacoTypes(monaco);
          await loadTypesFromApi(monaco);
        }
      } catch (error) {
        console.error('初始化Monaco类型定义失败:', error);
      }
    };

    // 代码变化处理（Monaco编辑器）
    const onCodeChange = (value) => {
      currentCode.value = value;
      // 更新第一个文件的名称（如果代码中包含@name）
      if (activeFile.value && activeFile.value.id === 'file1') {
        updateFileNameFromCode(activeFile.value);
      }
      // 保存到localStorage（保存所有文件）
      saveAllFilesToStorage();
      
      // 更新代码问题列表
      setTimeout(() => {
        updateCodeProblems();
      }, 500);
    };
    
    // 原生编辑器变化处理（失去焦点时保存）
    const handleNativeEditorChange = () => {
      // 更新第一个文件的名称（如果代码中包含@name）
      if (activeFile.value && activeFile.value.id === 'file1') {
        updateFileNameFromCode(activeFile.value);
      }
      // 保存到localStorage（保存所有文件）
      saveAllFilesToStorage();
    };
    
    // 更新代码问题列表
    const updateCodeProblems = () => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        const monaco = editorRef.value.getMonaco && editorRef.value.getMonaco() || window.monaco;
        if (editor && monaco) {
          const model = editor.getModel();
          if (model) {
            // 获取所有诊断标记（包括语法错误、LSP问题等）
            const markers = monaco.editor.getModelMarkers({ resource: model.uri });
            codeProblems.value = markers;
            console.log(`[Playground] 检测到 ${markers.length} 个代码问题`, markers);
            
            // 移动端：更新当前行问题
            if (isMobile.value) {
              updateCurrentLineProblem();
            }
          }
        }
      }
    };
    
    // 更新当前行的问题信息（移动端）
    const updateCurrentLineProblem = () => {
      if (!isMobile.value) {
        currentLineProblem.value = null;
        return;
      }
      
      try {
        if (!editorRef.value || !editorRef.value.getEditor) {
          return;
        }
        
        const editor = editorRef.value.getEditor();
        if (!editor) {
          return;
        }
        
        const position = editor.getPosition();
        if (!position) {
          currentLineProblem.value = null;
          return;
        }
        
        // 查找当前行的问题
        const lineNumber = position.lineNumber;
        const problem = codeProblems.value.find(p => 
          p.startLineNumber <= lineNumber && p.endLineNumber >= lineNumber
        );
        
        currentLineProblem.value = problem || null;
      } catch (error) {
        // 忽略错误，不影响其他功能
        if (!error.message || !error.message.includes('Canceled')) {
          console.warn('[Playground] 更新当前行问题失败:', error);
        }
      }
    };
    
    // 初始化Monaco编辑器标记监听器
    const setupMarkersListener = () => {
      if (editorRef.value && editorRef.value.getMonaco) {
        const monaco = editorRef.value.getMonaco() || window.monaco;
        if (monaco && monaco.editor) {
          // 移除旧的监听器
          if (markersChangeListener) {
            markersChangeListener.dispose();
          }
          
          // 添加新的监听器
          markersChangeListener = monaco.editor.onDidChangeMarkers((uris) => {
            // 当任何模型的标记发生变化时触发
            console.log('[Playground] Monaco标记变化', uris);
            updateCodeProblems();
          });
          
          console.log('[Playground] Monaco标记监听器已启用');
          
          // 立即更新一次
          setTimeout(() => {
            updateCodeProblems();
          }, 1000);
        }
        
        // 移动端：添加光标变化监听器
        if (isMobile.value && editorRef.value.getEditor) {
          const editor = editorRef.value.getEditor();
          if (editor) {
            // 移除旧的监听器
            if (cursorChangeListener) {
              cursorChangeListener.dispose();
            }
            
            // 添加光标位置变化监听器
            cursorChangeListener = editor.onDidChangeCursorPosition(() => {
              try {
                updateCurrentLineProblem();
              } catch (error) {
                // 忽略Monaco Editor的Canceled错误
                if (!error.message || !error.message.includes('Canceled')) {
                  console.error('[Playground] 更新问题提示失败:', error);
                }
              }
            });
            
            console.log('[Playground] 光标变化监听器已启用（移动端）');
          }
        }
      }
    };
    
    // 保存所有文件到localStorage
    const saveAllFilesToStorage = () => {
      const filesData = files.value.map(f => ({
        id: f.id,
        name: f.name,
        content: f.content,
        language: f.language || getLanguageFromFile(f.name),
        pinned: f.pinned || false,
        dbId: f.dbId || null
      }));
      localStorage.setItem('playground_files', JSON.stringify(filesData));
      localStorage.setItem('playground_active_file', activeFileId.value);
    };
    
    // 从localStorage加载所有文件
    const loadAllFilesFromStorage = () => {
      const savedFiles = localStorage.getItem('playground_files');
      if (savedFiles) {
        try {
          const filesData = JSON.parse(savedFiles);
          files.value = filesData.map(f => ({
            ...f,
            language: f.language || getLanguageFromFile(f.name),
            pinned: f.pinned || false,
            dbId: f.dbId || null,
            modified: false
          }));
          const savedActiveFile = localStorage.getItem('playground_active_file');
          if (savedActiveFile && files.value.find(f => f.id === savedActiveFile)) {
            activeFileId.value = savedActiveFile;
          }
        } catch (e) {
          console.warn('加载文件列表失败', e);
        }
      }
    };
    
    // 文件切换处理
    const handleFileChange = (fileId) => {
      // 标记正在切换文件，防止触发修改标记
      isFileChanging.value = true;
      activeFileId.value = fileId;
      saveAllFilesToStorage();
      
      // 获取切换后的文件
      const newFile = files.value.find(f => f.id === fileId);
      
      // 等待编辑器更新
      nextTick(() => {
        if (editorRef.value && editorRef.value.getEditor) {
          const editor = editorRef.value.getEditor();
          if (editor) {
            editor.focus();
            
            // 更新编辑器语言模式
            if (newFile) {
              const language = newFile.language || getLanguageFromFile(newFile.name);
              updateEditorLanguage(language);
            }
            
            // 更新代码问题列表
            setTimeout(() => {
              updateCodeProblems();
            }, 500);
          }
        }
        // 切换完成后，取消标记
        setTimeout(() => {
          isFileChanging.value = false;
        }, 100);
      });
    };
    
    // 删除文件
    const removeFile = (fileId) => {
      if (files.value.length <= 1) {
        ElMessage.warning('至少需要保留一个文件');
        return;
      }
      const index = files.value.findIndex(f => f.id === fileId);
      if (index !== -1) {
        files.value.splice(index, 1);
        // 如果删除的是当前活动文件，切换到第一个文件
        if (activeFileId.value === fileId) {
          activeFileId.value = files.value[0].id;
        }
        saveAllFilesToStorage();
      }
    };
    
    // 显示新建文件对话框
    const showNewFileDialog = () => {
      newFileForm.value = {
        name: '',
        identifier: '',
        author: '',
        match: '',
        language: 'javascript'
      };
      newFileDialogVisible.value = true;
    };
    
    // 模板生成函数已从 @/templates 模块导入
    // generateTemplate(name, identifier, author, match, language)
    
    // 创建新文件
    const createNewFile = async () => {
      if (!newFileFormRef.value) return;
      
      try {
        // 使用 Promise 模式进行表单验证
        const valid = await newFileFormRef.value.validate();
        if (!valid) return;
      } catch (e) {
        // 验证失败
        return;
      }
      
      const language = newFileForm.value.language || 'javascript';
      const isPython = language === 'python';
      const fileExt = isPython ? '.py' : '.js';
      
      // 使用解析器名称作为文件名
      let fileName = newFileForm.value.name;
      if (!fileName.endsWith(fileExt)) {
        // 移除可能的错误扩展名
        fileName = fileName.replace(/\.(js|py)$/i, '');
        fileName = fileName + fileExt;
      }
      
      // 检查文件名是否已存在
      if (files.value.some(f => f.name === fileName)) {
        ElMessage.warning('文件名已存在，请使用其他名称');
        return;
      }
      
      // 生成模板代码
      const template = generateTemplate(
        newFileForm.value.name.replace(/\.(js|py)$/i, ''),
        newFileForm.value.identifier,
        newFileForm.value.author,
        newFileForm.value.match,
        language
      );
      
      // 创建新文件
      fileIdCounter.value++;
      const newFile = {
        id: 'file' + fileIdCounter.value,
        name: fileName,
        content: template,
        language: language,
        modified: false
      };
      
      files.value.push(newFile);
      
      // 关闭对话框并保存
      newFileDialogVisible.value = false;
      saveAllFilesToStorage();
      
      // 使用 nextTick 确保 DOM 更新后再切换选项卡
      await nextTick();
      
      // 设置活动文件 ID（此时 DOM 已更新，tab 已存在）
      activeFileId.value = newFile.id;
      
      // 更新编辑器语言模式
      updateEditorLanguage(language);
      
      ElMessage.success(`${isPython ? 'Python' : 'JavaScript'}文件创建成功`);
      
      // 等待编辑器更新后聚焦
      await nextTick();
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          editor.focus();
        }
      }
    };
    
    // IDE功能：复制全部
    const copyAll = async () => {
      try {
        await navigator.clipboard.writeText(currentCode.value);
        ElMessage.success('已复制全部内容到剪贴板');
      } catch (error) {
        ElMessage.error('复制失败: ' + error.message);
      }
    };
    
    // IDE功能：粘贴 - 支持原生文本框和移动端输入法
    const pasteCode = async () => {
      try {
        const text = await navigator.clipboard.readText();
        
        if (!text) {
          ElMessage.warning('剪贴板为空');
          return;
        }

        if (editorRef.value && editorRef.value.getEditor) {
          const editor = editorRef.value.getEditor();
          if (editor) {
            const model = editor.getModel();
            if (!model) {
              ElMessage.error('编辑器未就绪');
              return;
            }

            // 获取当前选择范围，如果没有选择则使用光标位置
            const selection = editor.getSelection();
            const range = selection || new (window.monaco?.Range || editor.getModel().constructor.Range)(1, 1, 1, 1);

            // 使用executeEdits执行粘贴操作，支持一次多行粘贴
            const edits = [{
              range: range,
              text: text,
              forceMoveMarkers: true
            }];

            editor.executeEdits('paste-command', edits, [(selection || range)]);
            editor.focus();
            
            const lineCount = text.split('\n').length;
            ElMessage.success(`已粘贴 ${lineCount} 行内容`);
          }
        } else {
          ElMessage.error('编辑器未加载');
        }
      } catch (error) {
        if (error.name === 'NotAllowedError') {
          // 权限被拒绝，提示用户使用Ctrl+V
          ElMessage.warning('粘贴权限被拒绝，请使用 Ctrl+V 快捷键');
        } else {
          console.error('粘贴失败:', error);
          ElMessage.error('粘贴失败: ' + (error.message || '请使用 Ctrl+V'));
        }
      }
    };
    
    // IDE功能：全选
    const selectAll = () => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          editor.setSelection(editor.getModel().getFullModelRange());
          editor.focus();
        }
      }
    };
    
    // 更新编辑器语言模式
    const updateEditorLanguage = (language) => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          const model = editor.getModel();
          if (model) {
            try {
              // 尝试从编辑器实例获取 monaco
              const monaco = editorRef.value.getMonaco && editorRef.value.getMonaco() || window.monaco;
              if (monaco && monaco.editor && monaco.editor.setModelLanguage) {
                const langId = language === 'python' ? 'python' : 'javascript';
                monaco.editor.setModelLanguage(model, langId);
                console.log(`[Playground] 已切换编辑器语言为: ${langId}`);
              } else {
                console.warn('[Playground] Monaco 实例不可用，无法切换语言');
              }
            } catch (error) {
              console.error('[Playground] 切换编辑器语言失败:', error);
            }
          }
        }
      }
    };
    
    // 根据文件扩展名获取语言类型
    const getLanguageFromFile = (fileName) => {
      if (fileName && fileName.endsWith('.py')) {
        return 'python';
      }
      return 'javascript';
    };
    
    // ===== Python LSP 功能 =====
    // 初始化 pylsp 客户端
    const initPylspClient = async () => {
      try {
        console.log('[Playground] 初始化 Python LSP 客户端...');
        
        pylspClient = new PylspClient({
          onDiagnostics: (uri, markers) => {
            // 更新编辑器诊断信息
            if (editorRef.value && editorRef.value.getEditor) {
              const editor = editorRef.value.getEditor();
              const monaco = editorRef.value.getMonaco && editorRef.value.getMonaco() || window.monaco;
              if (editor && monaco) {
                const model = editor.getModel();
                if (model) {
                  monaco.editor.setModelMarkers(model, 'pylsp', markers);
                  // 更新代码问题列表（用于移动端显示）
                  codeProblems.value = markers;
                  console.log(`[Playground] 已更新 ${markers.length} 个诊断标记`);
                }
              }
            }
          },
          onConnected: () => {
            pylspConnected.value = true;
            console.log('[Playground] Python LSP 已连接');
            ElMessage.success('Python 语言服务器已连接');
            
            // 如果当前文件是 Python，打开文档
            if (activeFile.value && getLanguageFromFile(activeFile.value.name) === 'python') {
              syncPythonDocument();
            }
          },
          onDisconnected: () => {
            pylspConnected.value = false;
            console.log('[Playground] Python LSP 已断开');
          },
          onError: (error) => {
            console.error('[Playground] Python LSP 错误:', error);
          }
        });
        
        await pylspClient.connect();
      } catch (error) {
        console.error('[Playground] pylsp 初始化失败:', error);
        throw error;
      }
    };
    
    // 同步 Python 文档到 LSP
    const syncPythonDocument = () => {
      if (!pylspClient || !pylspClient.initialized) {
        return;
      }
      
      const file = activeFile.value;
      if (!file) {
        return;
      }
      
      const language = getLanguageFromFile(file.name);
      if (language !== 'python') {
        return;
      }
      
      console.log('[Playground] 同步 Python 文档到 LSP');
      pylspClient.openDocument(file.content, `file:///${file.name}`);
    };
    
    // 监听 Python 文件内容变化
    let pylspUpdateTimer = null;
    watch(() => currentCode.value, (newContent) => {
      if (!activeFile.value) return;
      
      const language = getLanguageFromFile(activeFile.value.name);
      if (language === 'python' && pylspClient && pylspClient.initialized) {
        // 防抖：延迟500ms更新
        if (pylspUpdateTimer) {
          clearTimeout(pylspUpdateTimer);
        }
        pylspUpdateTimer = setTimeout(() => {
          console.log('[Playground] 更新 Python 文档内容');
          pylspClient.updateDocument(newContent, `file:///${activeFile.value.name}`);
        }, 500);
      }
    });
    
    // 监听文件切换
    watch(activeFileId, () => {
      const file = activeFile.value;
      if (!file) return;
      
      const language = getLanguageFromFile(file.name);
      if (language === 'python' && pylspClient && pylspClient.initialized) {
        syncPythonDocument();
      }
    });
    
    // ===== IDE 功能 =====
    // IDE功能：切换自动换行
    const toggleWordWrap = () => {
      wordWrapEnabled.value = !wordWrapEnabled.value;
      // 更新编辑器选项
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          editor.updateOptions({ wordWrap: wordWrapEnabled.value ? 'on' : 'off' });
        }
      }
      ElMessage.success(wordWrapEnabled.value ? '已开启自动换行' : '已关闭自动换行');
    };
    
    // IDE功能：导出当前文件
    const exportCurrentFile = () => {
      if (!activeFile.value || !activeFile.value.content) {
        ElMessage.warning('当前文件为空，无法导出');
        return;
      }
      
      try {
        const blob = new Blob([activeFile.value.content], { type: 'text/javascript;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = activeFile.value.name;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        ElMessage.success('文件导出成功');
      } catch (error) {
        ElMessage.error('导出失败: ' + error.message);
      }
    };
    
    // IDE功能：撤销
    const undo = () => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          try {
            editor.trigger('keyboard', 'undo', null);
            editor.focus();
          } catch (error) {
            // 忽略Monaco Editor的Canceled错误
            if (!error.message || !error.message.includes('Canceled')) {
              console.error('撤销操作失败:', error);
            }
          }
        }
      }
    };
    
    // IDE功能：重做
    const redo = () => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          try {
            editor.trigger('keyboard', 'redo', null);
            editor.focus();
          } catch (error) {
            // 忽略Monaco Editor的Canceled错误
            if (!error.message || !error.message.includes('Canceled')) {
              console.error('重做操作失败:', error);
            }
          }
        }
      }
    };
    
    // 从代码中提取解析器名称
    const extractParserName = (code) => {
      if (!code) return null;
      const match = code.match(/@name\s+([^\r\n]+)/);
      if (match && match[1]) {
        return match[1].trim();
      }
      return null;
    };
    
    // 更新文件名称（从代码中提取）
    const updateFileNameFromCode = (file) => {
      if (!file || file.id !== 'file1') return; // 只更新第一个文件
      const parserName = extractParserName(file.content);
      if (parserName) {
        const newName = parserName.endsWith('.js') ? parserName : parserName + '.js';
        if (file.name !== newName) {
          file.name = newName;
          saveAllFilesToStorage();
        }
      }
    };

    // 加载示例代码
    const loadTemplate = async () => {
      if (activeFile.value) {
        try {
          // 根据当前语言从服务器加载示例代码
          const language = activeFile.value.language;
          const exampleContent = await playgroundApi.getExampleParser(language);
          
          activeFile.value.content = exampleContent;
          activeFile.value.modified = true;
          
          // 重置测试参数为示例链接
          testParams.value.shareUrl = 'https://example.com/s/abc';
          testParams.value.pwd = '';
          testParams.value.method = 'parse';
          // 清空测试结果
          testResult.value = null;
          consoleLogs.value = [];
          
          const languageName = language === 'python' ? 'Python' : 'JavaScript';
          ElMessage.success(`已加载${languageName}示例代码`);
        } catch (error) {
          ElMessage.error('加载示例代码失败: ' + error.message);
          console.error('Failed to load example code:', error);
        }
      }
    };

    // 格式化代码
    const formatCode = () => {
      // 原生编辑器模式下不支持格式化
      if (useNativeEditor.value) {
        ElMessage.warning('原生编辑器模式不支持代码格式化，请切换到Monaco编辑器');
        return;
      }
      
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          editor.getAction('editor.action.formatDocument').run();
        }
      }
    };

    // 保存代码
    const saveCode = () => {
      if (activeFile.value) {
        activeFile.value.modified = false;
        saveAllFilesToStorage();
        ElMessage.success('代码已保存');
      }
    };

    // 加载代码（已废弃，使用多文件管理）
    const loadCode = () => {
      loadAllFilesFromStorage();
      ElMessage.success('代码已加载');
    };

    // 清空代码
    const clearCode = () => {
      if (activeFile.value) {
        activeFile.value.content = '';
        activeFile.value.modified = true;
      }
      testResult.value = null;
    };
    
    // 切换原生编辑器
    const toggleNativeEditor = () => {
      useNativeEditor.value = !useNativeEditor.value;
      const mode = useNativeEditor.value ? '原生文本框' : 'Monaco编辑器';
      ElMessage.success(`已切换到 ${mode}`);
      
      if (useNativeEditor.value) {
        // 切换到原生编辑器：清理Monaco相关功能
        
        // 1. 移除Monaco标记监听器
        if (markersChangeListener) {
          markersChangeListener.dispose();
          markersChangeListener = null;
        }
        
        // 2. 移除光标变化监听器
        if (cursorChangeListener) {
          cursorChangeListener.dispose();
          cursorChangeListener = null;
        }
        
        // 3. 清空代码问题列表
        codeProblems.value = [];
        currentLineProblem.value = null;
        
        // 4. 自动聚焦到原生编辑器
        nextTick(() => {
          if (nativeEditorRef.value) {
            nativeEditorRef.value.focus();
          }
        });
        
        console.log('[Playground] 已切换到原生编辑器，Monaco功能已禁用');
      } else {
        // 切换回Monaco编辑器：重新启用Monaco功能
        nextTick(() => {
          // 重新初始化Monaco标记监听器
          setupMarkersListener();
          console.log('[Playground] 已切换到Monaco编辑器，Monaco功能已启用');
        });
      }
    };

    // 导入文件 - 触发文件选择对话框
    const importFile = () => {
      if (fileImportInput.value) {
        fileImportInput.value.click();
      }
    };

    // 处理文件导入 - 读取文件内容并替换当前代码
    const handleFileImport = async (event) => {
      const file = event.target.files?.[0];
      if (!file) {
        return;
      }

      try {
        const fileContent = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = (e) => resolve(e.target.result);
          reader.onerror = () => reject(new Error('文件读取失败'));
          reader.readAsText(file, 'UTF-8');
        });

        if (activeFile.value) {
          activeFile.value.content = fileContent;
          activeFile.value.modified = true;
          // 更新文件名
          activeFile.value.name = file.name;
          // 根据文件扩展名识别语言
          const ext = file.name.split('.').pop().toLowerCase();
          if (ext === 'py') {
            activeFile.value.language = 'python';
          } else if (ext === 'js' || ext === 'txt') {
            activeFile.value.language = 'javascript';
          }
          saveAllFilesToStorage();
          ElMessage.success(`文件"${file.name}"已导入，大小：${(file.size / 1024).toFixed(2)}KB`);
        }
      } catch (error) {
        ElMessage.error('导入失败: ' + error.message);
        console.error('文件导入错误:', error);
      }

      // 重置input的value，允许再次选择同一文件
      if (fileImportInput.value) {
        fileImportInput.value.value = '';
      }
    };

    // 语言切换处理
    // 执行测试
    const executeTest = async () => {
      const codeToTest = currentCode.value;
      
      // 获取当前文件的语言类型
      const currentLanguage = activeFile.value?.language || getLanguageFromFile(activeFile.value?.name) || 'javascript';
      const isPython = currentLanguage === 'python';
      
      if (!codeToTest.trim()) {
        ElMessage.warning(`请先输入${isPython ? 'Python' : 'JavaScript'}代码`);
        return;
      }

      if (!testParams.value.shareUrl.trim()) {
        ElMessage.warning('请输入分享链接');
        return;
      }
      
      // 检查代码中是否包含潜在的危险模式（仅针对JavaScript）
      if (!isPython) {
        const dangerousPatterns = [
          { pattern: /while\s*\(\s*true\s*\)/gi, message: '检测到 while(true) 无限循环' },
          { pattern: /for\s*\(\s*;\s*;\s*\)/gi, message: '检测到 for(;;) 无限循环' },
          { pattern: /for\s*\(\s*var\s+\w+\s*=\s*\d+\s*;\s*true\s*;/gi, message: '检测到可能的无限循环' }
        ];
        
        for (const { pattern, message } of dangerousPatterns) {
          if (pattern.test(codeToTest)) {
            const confirmed = await ElMessageBox.confirm(
              `⚠️ ${message}\n\n这可能导致脚本无法停止并占用服务器资源。\n\n建议修改代码，添加合理的循环退出条件。\n\n确定要继续执行吗？`,
              '危险代码警告',
              {
                confirmButtonText: '我知道风险，继续执行',
                cancelButtonText: '取消',
                type: 'warning',
                dangerouslyUseHTMLString: true
              }
            ).catch(() => false);
            
            if (!confirmed) {
              return;
            }
            break;
          }
        }
      }
      
      // Python 无限循环检查
      if (isPython) {
        const pythonDangerousPatterns = [
          { pattern: /while\s+True\s*:/gi, message: '检测到 while True: 无限循环' }
        ];
        
        for (const { pattern, message } of pythonDangerousPatterns) {
          if (pattern.test(codeToTest)) {
            const confirmed = await ElMessageBox.confirm(
              `⚠️ ${message}\n\n这可能导致脚本无法停止并占用服务器资源。\n\n建议修改代码，添加合理的循环退出条件。\n\n确定要继续执行吗？`,
              '危险代码警告',
              {
                confirmButtonText: '我知道风险，继续执行',
                cancelButtonText: '取消',
                type: 'warning',
                dangerouslyUseHTMLString: true
              }
            ).catch(() => false);
            
            if (!confirmed) {
              return;
            }
            break;
          }
        }
      }

      testing.value = true;
      testResult.value = null;
      consoleLogs.value = []; // 清空控制台

      try {
        const result = await playgroundApi.testScript(
          codeToTest,  // 使用当前活动文件的代码
          testParams.value.shareUrl,
          testParams.value.pwd,
          testParams.value.method,
          currentLanguage  // 传递语言类型
        );
        
        console.log('测试结果:', result);
        testResult.value = result;
        
        // 将日志添加到控制台
        if (result && result.logs && Array.isArray(result.logs) && result.logs.length > 0) {
          consoleLogs.value = [...result.logs];
        } else if (result && result.success) {
          // 即使没有日志，也显示一个成功信息
          consoleLogs.value = [{
            level: 'INFO',
            message: '执行成功',
            timestamp: Date.now()
          }];
        }
      } catch (error) {
        console.error('执行测试失败:', error);
        testResult.value = {
          success: false,
          error: error.message || '执行失败',
          logs: [],
          executionTime: 0
        };
        // 添加错误日志到控制台
        consoleLogs.value = [{
          level: 'ERROR',
          message: error.message || '执行失败',
          timestamp: Date.now()
        }];
      } finally {
        testing.value = false;
      }
    };

    // 清空控制台日志
    const clearConsoleLogs = () => {
      consoleLogs.value = [];
    };

    // 格式化时间
    const formatTime = (timestamp) => {
      const date = new Date(timestamp);
      return date.toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        fractionalSecondDigits: 3
      });
    };

    // 格式化日期时间
    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '';
      const date = new Date(dateTimeStr);
      return date.toLocaleString('zh-CN');
    };

    // 加载解析器列表
    const loadParserList = async () => {
      loadingList.value = true;
      try {
        const result = await playgroundApi.getParserList();
        console.log('获取解析器列表响应:', result);
        // 检查响应格式
        if (result.code === 200 || result.success) {
          console.log('列表数据:', result.data);
          parserList.value = result.data || [];
        } else if (result.data && Array.isArray(result.data)) {
          // 如果data直接是数组
          parserList.value = result.data;
        } else {
          console.error('无法解析列表数据格式:', result);
          ElMessage.error(result.msg || result.error || '加载列表失败');
        }
      } catch (error) {
        console.error('加载列表错误:', error);
        ElMessage.error(error.message || '加载列表失败');
      } finally {
        loadingList.value = false;
      }
    };

    // 发布解析器
    const publishParser = () => {
      const codeToPublish = currentCode.value;
      const currentLanguage = activeFile.value?.language || getLanguageFromFile(activeFile.value?.name) || 'javascript';
      const isPython = currentLanguage === 'python';
      
      if (!codeToPublish.trim()) {
        ElMessage.warning(`请先编写${isPython ? 'Python' : 'JavaScript'}代码`);
        return;
      }
      publishForm.value.jsCode = codeToPublish;
      publishForm.value.language = currentLanguage;
      publishDialogVisible.value = true;
    };

    // 确认发布
    const confirmPublish = async (forceOverwrite = false) => {
      publishing.value = true;
      try {
        const codeToPublish = currentCode.value;
        const currentLanguage = publishForm.value.language || 'javascript';
        const result = await playgroundApi.saveParser(codeToPublish, currentLanguage, forceOverwrite);
        console.log('保存解析器响应:', result);
        
        // 检查是否需要覆盖确认
        if (result.code !== 200 && result.existingId && result.existingType) {
          // type已存在，显示覆盖确认对话框
          publishing.value = false;
          overwriteInfo.value = {
            id: result.existingId,
            type: result.existingType,
            message: result.msg || result.error
          };
          
          ElMessageBox.confirm(
            `解析器类型 "${result.existingType}" 已存在，是否覆盖现有解析器？`,
            '确认覆盖',
            {
              confirmButtonText: '覆盖',
              cancelButtonText: '取消',
              type: 'warning',
              distinguishCancelAndClose: true
            }
          ).then(() => {
            // 用户确认覆盖，重新发布
            confirmPublish(true);
          }).catch(() => {
            // 用户取消
            ElMessage.info('已取消发布');
            overwriteInfo.value = null;
          });
          return;
        }
        
        // 检查响应格式
        if (result.code === 200 || result.success) {
          // 从响应或代码中提取type信息
          let parserType = '';
          try {
            const typeMatch = codeToPublish.match(/@type\s+(\w+)/);
            parserType = typeMatch ? typeMatch[1] : '';
          } catch (e) {
            console.warn('无法提取type', e);
          }
          
          // 构建API调用示例
          const baseUrl = window.location.origin;
          const exampleUrl = 'https://lanzoui.com/i7Aq12ab3cd';
          
          const apiExamples = `
<div style="text-align: left; padding: 0 20px;">
  <h3>✅ 发布成功！</h3>
  <p>解析器类型: <code>${parserType || '未知'}</code></p>
  
  <h4>📡 API调用示例：</h4>
  
  <div style="margin: 10px 0;">
    <strong>1. 302重定向（直接下载）</strong>
    <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto;">${baseUrl}/parser?url=${encodeURIComponent(exampleUrl)}</pre>
    <p style="color: #666; font-size: 12px;">浏览器访问该链接会自动跳转到下载地址</p>
  </div>
  
  <div style="margin: 10px 0;">
    <strong>2. JSON响应（获取解析结果）</strong>
    <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto;">${baseUrl}/json/parser?url=${encodeURIComponent(exampleUrl)}</pre>
    <p style="color: #666; font-size: 12px;">返回JSON格式的解析结果，包含下载链接等信息</p>
  </div>
  
  <div style="margin: 10px 0;">
    <strong>3. 带密码</strong>
    <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto;">${baseUrl}/parser?url=${encodeURIComponent(exampleUrl)}&pwd=1234</pre>
  </div>
  
  <h4>🔧 curl命令示例：</h4>
  <pre style="background: #2d2d2d; color: #fff; padding: 10px; border-radius: 4px; overflow-x: auto;"># 302重定向
curl -L "${baseUrl}/parser?url=${encodeURIComponent(exampleUrl)}"

# JSON响应
curl "${baseUrl}/json/parser?url=${encodeURIComponent(exampleUrl)}"</pre>
  
  <p style="margin-top: 15px; color: #409eff;">
    💡 提示：将示例链接替换为实际的分享链接即可使用
  </p>
</div>`;
          
          ElMessageBox.alert(apiExamples, '发布成功', {
            dangerouslyUseHTMLString: true,
            confirmButtonText: '知道了',
            customClass: 'api-example-dialog'
          });
          
          publishDialogVisible.value = false;
          overwriteInfo.value = null; // 清理覆盖信息
          // 切换到列表标签页并刷新
          activeTab.value = 'list';
          await loadParserList();
        } else {
          console.error('保存失败响应:', result);
          ElMessage.error(result.msg || result.error || '发布失败');
        }
      } catch (error) {
        console.error('发布失败错误:', error);
        ElMessage.error(error.message || '发布失败');
      } finally {
        publishing.value = false;
      }
    };

    // 加载解析器到编辑器（添加到新的文件tab标签）
    const loadParserToEditor = async (parser) => {
      try {
        // 先检查是否已存在相同 dbId 的文件（防止重复打开）
        const existingFile = files.value.find(f => f.dbId === parser.id);
        if (existingFile) {
          // 如果已存在，直接切换到该文件
          activeFileId.value = existingFile.id;
          activeTab.value = 'editor';
          ElMessage.info('文件已打开，已切换到该标签');
          return;
        }
        
        const result = await playgroundApi.getParserById(parser.id);
        if (result.code === 200 && result.data) {
          // 从代码中提取文件名
          const code = result.data.jsCode;
          const isPython = parser.language === 'python' || result.data.language === 'python';
          const fileExt = isPython ? '.py' : '.js';
          let fileName = parser.name || ('解析器' + fileExt);
          
          // 尝试从@name提取文件名
          const nameMatch = code.match(/@name\s+([^\r\n]+)/);
          if (nameMatch && nameMatch[1]) {
            const parserName = nameMatch[1].trim();
            // 移除可能的错误扩展名并添加正确的
            fileName = parserName.replace(/\.(js|py)$/i, '') + fileExt;
          }
          
          // 检查文件名是否已存在，如果存在则添加序号
          let finalFileName = fileName;
          let counter = 1;
          while (files.value.some(f => f.name === finalFileName)) {
            const nameWithoutExt = fileName.replace(/\.(js|py)$/i, '');
            finalFileName = `${nameWithoutExt}_${counter}${fileExt}`;
            counter++;
          }
          
          // 创建新文件，包含数据库ID
          fileIdCounter.value++;
          const newFile = {
            id: 'file' + fileIdCounter.value,
            name: finalFileName,
            content: code,
            language: isPython ? 'python' : 'javascript',
            modified: false,
            pinned: false,
            dbId: parser.id  // 保存数据库中的ID
          };
          
          files.value.push(newFile);
          activeFileId.value = newFile.id;
          activeTab.value = 'editor';
          saveAllFilesToStorage();
          
          ElMessage.success('已添加到新文件标签');
          
          // 等待编辑器更新后聚焦
          nextTick(() => {
            if (editorRef.value && editorRef.value.getEditor) {
              const editor = editorRef.value.getEditor();
              if (editor) {
                editor.focus();
              }
            }
          });
        } else {
          ElMessage.error('加载失败');
        }
      } catch (error) {
        ElMessage.error(error.message || '加载失败');
      }
    };

    // 删除解析器
    const deleteParser = async (id) => {
      try {
        await ElMessageBox.confirm('确定要删除这个解析器吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        });

        const result = await playgroundApi.deleteParser(id);
        if (result.code === 200) {
          ElMessage.success('删除成功');
          await loadParserList();
        } else {
          ElMessage.error(result.msg || '删除失败');
        }
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error(error.message || '删除失败');
        }
      }
    };

    // 标签页切换
    const handleTabChange = (tabName) => {
      if (tabName === 'list') {
        loadParserList();
      }
    };

    // ===== 主题切换功能 =====
    const changeTheme = (themeName) => {
      currentTheme.value = themeName;
      const theme = themes.find(t => t.name === themeName);
      if (theme) {
        // 同步更新 isDarkMode
        isDarkMode.value = theme.page === 'dark';
        
        // 切换页面主题
        const html = document.documentElement;
        const body = document.body;
        
        if (html && body && html.classList && body.classList) {
          if (theme.page === 'dark') {
            html.classList.add('dark');
            body.classList.add('dark-theme');
            // 设置背景色
            body.style.backgroundColor = '#0a0a0a';
          } else {
            html.classList.remove('dark');
            body.classList.remove('dark-theme');
            // 设置背景色
            body.style.backgroundColor = '#f0f2f5';
          }
        }
        
        // 编辑器主题会通过computed自动更新
        localStorage.setItem('playground_theme', themeName);
        // 强制更新splitpanes分隔线样式
        updateSplitpanesStyle();
        ElMessage.success(`已切换到${themeName}主题`);
      }
    };
    
    const toggleTheme = () => {
      const currentIndex = themes.findIndex(t => t.name === currentTheme.value);
      const nextIndex = (currentIndex + 1) % themes.length;
      changeTheme(themes[nextIndex].name);
    };
    
    // ===== 折叠/展开功能 =====
    const togglePanel = (panelName) => {
      collapsedPanels.value[panelName] = !collapsedPanels.value[panelName];
      localStorage.setItem('playground_collapsed_panels', JSON.stringify(collapsedPanels.value));
    };
    
    const toggleRightPanel = () => {
      collapsedPanels.value.rightPanel = !collapsedPanels.value.rightPanel;
      localStorage.setItem('playground_collapsed_panels', JSON.stringify(collapsedPanels.value));
    };
    
    // 处理分栏大小调整
    const handleResize = (panes) => {
      if (panes && Array.isArray(panes)) {
        splitSizes.value = panes.map(p => p.size || 50);
      }
      // 调整大小后更新分隔线样式
      updateSplitpanesStyle();
    };
    
    // ===== 全屏功能 =====
    const { isFullscreen: isDocumentFullscreen, toggle: toggleDocumentFullscreen } = useFullscreen();
    
    const toggleFullscreen = () => {
      isFullscreen.value = !isFullscreen.value;
      toggleDocumentFullscreen();
    };
    
    // ===== 快捷键帮助 =====
    const showShortcutsHelp = () => {
      shortcutsDialogVisible.value = true;
    };
    
    // ===== 代码问题相关 =====
    // 显示代码问题对话框（移动端）
    const showProblemsDialog = () => {
      problemsDialogVisible.value = true;
    };
    
    // 跳转到问题行
    const goToProblemLine = (problem) => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          // 跳转到问题所在行
          editor.revealLineInCenter(problem.startLineNumber);
          // 设置光标位置
          editor.setPosition({
            lineNumber: problem.startLineNumber,
            column: problem.startColumn || 1
          });
          // 聚焦编辑器
          editor.focus();
          // 关闭对话框
          problemsDialogVisible.value = false;
        }
      }
    };
    
    // ===== URL历史记录功能 =====
    // 添加URL到历史记录
    const addToUrlHistory = (url) => {
      if (!url || !url.trim()) return;
      
      // 去重并添加到开头
      const filtered = urlHistory.value.filter(item => item !== url);
      filtered.unshift(url);
      
      // 限制数量
      if (filtered.length > MAX_HISTORY) {
        filtered.length = MAX_HISTORY;
      }
      
      urlHistory.value = filtered;
      localStorage.setItem(HISTORY_KEY, JSON.stringify(filtered));
    };
    
    // 移动端执行测试包装函数
    const handleMobileExecuteTest = async () => {
      await executeTest();
      // 如果执行成功，添加到历史记录
      if (testResult.value && testResult.value.success) {
        addToUrlHistory(testParams.value.shareUrl);
        ElMessage.success('测试执行成功');
      }
    };
    
    // 移动端快速测试（使用当前参数直接执行）
    const handleMobileQuickTest = async () => {
      if (testing.value) return;
      
      // 检查是否有测试参数
      if (!testParams.value.shareUrl || !testParams.value.shareUrl.trim()) {
        ElMessage.warning('请先设置测试参数');
        mobileTestDialogVisible.value = true;
        return;
      }
      
      await handleMobileExecuteTest();
    };
    
    // 长按定时器
    let longPressTimer = null;
    const LONG_PRESS_DURATION = 500; // 500ms
    
    // 处理运行按钮触摸开始
    const handleRunButtonTouchStart = (e) => {
      // 清除之前的定时器
      if (longPressTimer) {
        clearTimeout(longPressTimer);
      }
      
      // 设置长按定时器
      longPressTimer = setTimeout(() => {
        // 长按触发：打开测试弹框
        e.preventDefault();
        mobileTestDialogVisible.value = true;
        longPressTimer = null;
      }, LONG_PRESS_DURATION);
    };
    
    // 处理运行按钮触摸结束
    const handleRunButtonTouchEnd = () => {
      // 如果定时器还在，说明是短按（点击）
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
        // 短按由 @click 事件处理，这里不需要做任何事
      }
    };
    
    // ===== 快捷键系统 =====
    const keys = useMagicKeys();
    const ctrlEnter = keys['Ctrl+Enter'];
    const cmdEnter = keys['Meta+Enter'];
    const ctrlS = keys['Ctrl+S'];
    const cmdS = keys['Meta+S'];
    const shiftAltF = keys['Shift+Alt+F'];
    const f11 = keys['F11'];
    const ctrlL = keys['Ctrl+L'];
    const cmdL = keys['Meta+L'];
    const ctrlR = keys['Ctrl+R'];
    const cmdR = keys['Meta+R'];
    const ctrlSlash = keys['Ctrl+/'];
    const cmdSlash = keys['Meta+/'];
    
    // 执行测试 - Ctrl/Cmd + Enter
    watch([ctrlEnter, cmdEnter], ([ctrl, cmd]) => {
      if (ctrl || cmd) {
        executeTest();
      }
    });
    
    // 保存代码 - Ctrl/Cmd + S
    watch([ctrlS, cmdS], ([ctrl, cmd]) => {
      if (ctrl || cmd) {
        saveCode();
      }
    });
    
    // 格式化代码 - Shift + Alt + F（仅Monaco编辑器模式）
    watch(shiftAltF, (pressed) => {
      if (pressed && !useNativeEditor.value) {
        formatCode();
      }
    });
    
    // 全屏模式 - F11
    watch(f11, (pressed) => {
      if (pressed) {
        toggleFullscreen();
      }
    });
    
    // 清空控制台 - Ctrl/Cmd + L
    watch([ctrlL, cmdL], ([ctrl, cmd]) => {
      if (ctrl || cmd) {
        clearConsoleLogs();
      }
    });
    
    // 重置代码 - Ctrl/Cmd + R
    watch([ctrlR, cmdR], ([ctrl, cmd]) => {
      if (ctrl || cmd) {
        loadTemplate();
      }
    });
    
    // 快捷键帮助 - Ctrl/Cmd + /
    watch([ctrlSlash, cmdSlash], ([ctrl, cmd]) => {
      if (ctrl || cmd) {
        showShortcutsHelp();
      }
    });
    
    // 阻止浏览器默认快捷键
    useEventListener('keydown', (e) => {
      // 阻止 Ctrl/Cmd + S 默认保存
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
      }
      // 阻止 Ctrl/Cmd + R 默认刷新
      if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
        e.preventDefault();
      }
      // 阻止 F11 默认全屏
      if (e.key === 'F11') {
        e.preventDefault();
      }
    });
    
    // 检查暗色主题
    const checkDarkMode = () => {
      try {
        const html = document.documentElement;
        const body = document.body;
        if (!html || !body) {
          return; // DOM未准备好，直接返回
        }
        
        if (html.classList) {
          isDarkMode.value = html.classList.contains('dark') || 
                            html.getAttribute('data-theme') === 'dark';
          // 强制更新splitpanes分隔线样式
          updateSplitpanesStyle();
        }
      } catch (error) {
        console.warn('检查暗色主题失败:', error);
      }
    };
    
    // 强制更新splitpanes分隔线样式
    const updateSplitpanesStyle = () => {
      setTimeout(() => {
        try {
          const html = document.documentElement;
          const body = document.body;
          if (!html || !body) {
            return; // DOM未准备好，直接返回
          }
          
          const splitters = document.querySelectorAll('.splitpanes__splitter');
          const isDark = html.classList?.contains('dark') || 
                        body.classList?.contains('dark-theme');
          
          splitters.forEach(splitter => {
            if (splitter) {
              if (isDark) {
                splitter.style.setProperty('background-color', 'rgba(255, 255, 255, 0.08)', 'important');
                splitter.style.setProperty('background', 'rgba(255, 255, 255, 0.08)', 'important');
              } else {
                splitter.style.removeProperty('background-color');
                splitter.style.removeProperty('background');
              }
            }
          });
        } catch (error) {
          console.warn('更新splitpanes样式失败:', error);
        }
      }, 100);
    };

    onMounted(async () => {
      // 加载URL历史记录
      const savedHistory = localStorage.getItem(HISTORY_KEY);
      if (savedHistory) {
        try {
          urlHistory.value = JSON.parse(savedHistory);
        } catch (e) {
          console.error('加载URL历史失败:', e);
          urlHistory.value = [];
        }
      }
      
      // 初始化移动端检测
      updateIsMobile();
      window.addEventListener('resize', updateIsMobile);
      
      // 添加页面关闭/刷新前的提示
      window.addEventListener('beforeunload', handleBeforeUnload);
      
      // 添加点击事件关闭右键菜单
      document.addEventListener('click', hideTabContextMenu);
      
      // 检查认证状态
      const isAuthed = await checkAuthStatus();
      
      // 如果已认证，初始化playground
      if (isAuthed) {
        await initPlayground();
      } else {
        // 未认证，停止加载动画，显示密码输入
        loading.value = false;
      }
      
      await nextTick();
      checkDarkMode();

      // 监听主题变化
      const html = document.documentElement;
      if (html && html.classList) {
        try {
          const observer = new MutationObserver(() => {
            checkDarkMode();
          });
          observer.observe(html, {
            attributes: true,
            attributeFilter: ['class', 'data-theme']
          });
        } catch (error) {
          console.warn('创建主题监听器失败:', error);
        }
      }
      
      // 初始化splitpanes样式
      updateSplitpanesStyle();
      
      // 初始化Monaco标记监听器
      setTimeout(() => {
        setupMarkersListener();
      }, 2000);
    });
    
    onUnmounted(() => {
      window.removeEventListener('resize', updateIsMobile);
      // 移除页面关闭/刷新前的提示
      window.removeEventListener('beforeunload', handleBeforeUnload);
      // 移除右键菜单关闭事件
      document.removeEventListener('click', hideTabContextMenu);
      // 断开 pylsp 连接
      if (pylspClient) {
        pylspClient.disconnect();
        pylspClient = null;
      }
      // 清理Monaco标记监听器
      if (markersChangeListener) {
        markersChangeListener.dispose();
        markersChangeListener = null;
      }
    });

    return {
      LANGUAGE,
      editorRef,
      jsCode,
      currentCode,
      testParams,
      testResult,
      testing,
      isDarkMode,
      editorTheme,
      shouldShowAuthUI,
      editorOptions,
      // 多文件管理
      files,
      activeFileId,
      activeFile,
      currentFileLanguageDisplay,
      currentEditorLanguage,
      handleFileChange,
      removeFile,
      // 标签页右键菜单
      tabContextMenu,
      showTabContextMenu,
      hideTabContextMenu,
      contextMenuAction,
      getFileTabLabel,
      isLastFile,
      // 新建文件
      newFileDialogVisible,
      newFileForm,
      newFileFormRef,
      newFileFormRules,
      showNewFileDialog,
      createNewFile,
      // IDE功能
      copyAll,
      pasteCode,
      selectAll,
      toggleWordWrap,
      wordWrapEnabled,
      exportCurrentFile,
      importFile,
      handleFileImport,
      fileImportInput,
      undo,
      redo,
      updateEditorLanguage,
      getLanguageFromFile,
      // 原生编辑器切换
      useNativeEditor,
      nativeEditorRef,
      toggleNativeEditor,
      handleNativeEditorChange,
      // 加载和认证
      loading,
      loadProgress,
      loadingMessage,
      authChecking,
      authed,
      inputPassword,
      authError,
      authLoading,
      playgroundEnabled,
      checkAuthStatus,
      submitPassword,
      goHome,
      goHomeInNewWindow,
      // 移动端
      isMobile,
      updateIsMobile,
      onCodeChange,
      loadTemplate,
      formatCode,
      saveCode,
      loadCode,
      clearCode,
      executeTest,
      formatTime,
      formatDateTime,
      activeTab,
      parserList,
      loadingList,
      publishDialogVisible,
      publishing,
      publishForm,
      loadParserList,
      publishParser,
      confirmPublish,
      loadParserToEditor,
      deleteParser,
      handleTabChange,
      helpCollapseActive,
      consoleLogs,
      clearConsoleLogs,
      // Python LSP
      pylspConnected,
      // 新增功能
      collapsedPanels,
      togglePanel,
      toggleRightPanel,
      currentTheme,
      themes,
      changeTheme,
      toggleTheme,
      isFullscreen,
      toggleFullscreen,
      shortcutsDialogVisible,
      showShortcutsHelp,
      shortcutsData,
      codeProblems,
      currentLineProblem,
      problemsDialogVisible,
      showProblemsDialog,
      goToProblemLine,
      updateCodeProblems,
      setupMarkersListener,
      splitSizes,
      playgroundContainer,
      handleResize,
      // URL历史记录
      urlHistory,
      addToUrlHistory,
      // 移动端模态框
      mobileTestDialogVisible,
      handleMobileExecuteTest,
      handleMobileQuickTest,
      handleRunButtonTouchStart,
      handleRunButtonTouchEnd,
      // 移动端编辑器拖拽
      mobileEditorHeight,
      startResize,
      isResizing
    };
  }
};
</script>

<style>
/* API示例对话框样式 */
.api-example-dialog {
  width: 80%;
  max-width: 900px;
}

.api-example-dialog .el-message-box__message {
  max-height: 70vh;
  overflow-y: auto;
}

.api-example-dialog code {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 13px;
  color: #e83e8c;
}

.api-example-dialog pre {
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  margin: 8px 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.api-example-dialog h4 {
  margin: 15px 0 10px 0;
  color: #303133;
}

.api-example-dialog strong {
  color: #409eff;
  display: block;
  margin-bottom: 5px;
}

/* ===== 全局暗色模式 Splitpanes 分隔线修复 ===== */
html.dark .splitpanes__splitter,
body.dark-theme .splitpanes__splitter,
.dark-theme .splitpanes__splitter,
.splitpanes.default-theme .splitpanes__splitter.dark-mode {
  background-color: rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

html.dark .splitpanes__splitter:hover,
body.dark-theme .splitpanes__splitter:hover,
.dark-theme .splitpanes__splitter:hover {
  background-color: var(--el-color-primary) !important;
  background: var(--el-color-primary) !important;
}
</style>

<style scoped>
/* ===== 加载动画和进度条 ===== */
.playground-loading-overlay {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.98);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(5px);
}

.dark-theme .playground-loading-overlay {
  background: rgba(10, 10, 10, 0.98);
}

.playground-loading-card {
  width: 320px;
  padding: 30px 40px;
  background: #fff;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  border-radius: 12px;
  text-align: center;
  font-size: 14px;
}

.dark-theme .playground-loading-card {
  background: #1f1f1f;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.5);
}

.loading-icon {
  margin-bottom: 20px;
  color: var(--el-color-primary);
}

.loading-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
}

.loading-bar {
  width: 100%;
  height: 6px;
  background: var(--el-fill-color-light);
  border-radius: 3px;
  margin: 12px 0;
  overflow: hidden;
}

.loading-bar-inner {
  height: 100%;
  background: linear-gradient(90deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  transition: width 0.3s ease;
  border-radius: 3px;
}

.loading-percent {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.loading-details {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-height: 20px;
}

/* ===== 认证界面 ===== */
.playground-auth-loading {
  position: fixed;
  inset: 0;
  background: var(--el-bg-color);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.playground-auth-overlay {
  position: fixed;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.playground-auth-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  border-radius: 16px;
  text-align: center;
}

.dark-theme .playground-auth-card {
  background: #1f1f1f;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.auth-icon {
  margin-bottom: 24px;
  color: var(--el-color-primary);
}

.auth-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 24px;
}

.auth-input {
  margin-bottom: 16px;
}

.auth-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--el-color-danger);
  font-size: 13px;
  margin-bottom: 16px;
}

.auth-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
}

/* ===== 容器布局 ===== */
.playground-container {
  padding: 10px 20px;
  min-height: calc(100vh - 20px);
  transition: all 0.3s ease;
}

/* 移动端：去掉边距，占满宽度 */
.playground-container.is-mobile {
  padding: 0;
  min-height: 100vh;
}

.playground-container.dark-theme {
  background-color: #0a0a0a;
}

.playground-container.fullscreen {
  padding: 0;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: var(--el-bg-color);
}

.playground-container.fullscreen.dark-theme {
  background: #0a0a0a;
}

.playground-card {
  max-width: 100%;
  height: 100%;
  transition: all 0.3s ease;
}

/* 移动端：卡片占满宽度，去掉边距 */
.playground-container.is-mobile .playground-card {
  margin: 0;
  border-radius: 0;
  border-left: none;
  border-right: none;
}

.playground-container.is-mobile .playground-card :deep(.el-card__header) {
  padding: 0 !important;
}

.playground-container.is-mobile .playground-card :deep(.el-card__body) {
  padding: 0 8px !important;
}

.dark-theme .playground-card {
  background: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .playground-card :deep(.el-card__header) {
  background: #1f1f1f;
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

/* ===== 顶部面包屑导航栏样式 ===== */
.breadcrumb-top-bar {
  background: var(--el-bg-color);
  padding: 12px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
  margin: -20px -20px 20px -20px;
}

.dark-theme .breadcrumb-top-bar {
  background: var(--el-bg-color-overlay);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* 移动端顶部面包屑 */
@media screen and (max-width: 768px) {
  .breadcrumb-top-bar {
    padding: 10px 15px;
    margin: -10px -10px 10px -10px;
  }
}

/* ===== 顶部面包屑导航栏样式 ===== */
.breadcrumb-top-bar {
  background: var(--el-bg-color);
  padding: 12px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
  margin: -20px -20px 20px -20px;
}

.dark-theme .breadcrumb-top-bar {
  background: var(--el-bg-color-overlay);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* 移动端顶部面包屑 */
@media screen and (max-width: 768px) {
  .breadcrumb-top-bar {
    padding: 10px 15px;
    margin: -10px -10px 10px -10px;
  }
}

/* ===== 工具栏样式 ===== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 15px;
}

.header-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  min-width: 0;
  gap: 4px;
}

.header-left-top {
  display: flex;
  align-items: center;
  width: 100%;
}

.header-left-bottom {
  display: flex;
  align-items: center;
  width: 100%;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* 面包屑导航样式 */
.breadcrumb-nav {
  display: flex;
  align-items: center;
  white-space: nowrap;
  flex-shrink: 0;
}

.breadcrumb-nav :deep(.el-breadcrumb) {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.breadcrumb-nav :deep(.el-breadcrumb__inner) {
  font-size: 14px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  display: inline-block;
}

.breadcrumb-nav :deep(.el-breadcrumb__inner.is-link) {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.breadcrumb-nav :deep(.el-breadcrumb__separator) {
  margin: 0 8px;
  white-space: nowrap;
}

.breadcrumb-link {
  display: inline-flex;
  align-items: center;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
  transition: color 0.2s;
}

.breadcrumb-link:hover {
  color: var(--el-color-primary);
}

.dark-theme .breadcrumb-link {
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .breadcrumb-link:hover {
  color: var(--el-color-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

/* 移动端：按钮左对齐 */
.playground-container.is-mobile .header-actions {
  justify-content: flex-start;
  width: 100%;
}

.playground-container.is-mobile .header-actions .el-button-group {
  margin-right: 0;
}

/* ===== Splitpanes样式 ===== */
.splitpanes {
  height: calc(100vh - 280px);
  min-height: 600px;
  background: transparent;
}

.splitpanes__pane {
  transition: all 0.3s ease;
  position: relative;
  background: transparent;
}

.splitpanes__splitter {
  background-color: var(--el-border-color-light);
  position: relative;
  transition: all 0.3s ease;
}

.splitpanes__splitter:hover {
  background-color: var(--el-color-primary);
  box-shadow: 0 0 8px rgba(64, 158, 255, 0.3);
}

.splitpanes--vertical > .splitpanes__splitter {
  width: 4px;
  margin: 0 -2px;
  cursor: col-resize;
}

/* 暗色模式下splitpanes分隔线 - 最强覆盖 */
.dark-theme .splitpanes__splitter,
.dark-theme.playground-container .splitpanes__splitter,
.playground-container.dark-theme .splitpanes__splitter,
body.dark-theme .splitpanes__splitter,
html.dark .splitpanes__splitter {
  background-color: rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

.dark-theme .splitpanes__splitter:hover,
.dark-theme.playground-container .splitpanes__splitter:hover,
.playground-container.dark-theme .splitpanes__splitter:hover,
body.dark-theme .splitpanes__splitter:hover,
html.dark .splitpanes__splitter:hover {
  background-color: var(--el-color-primary) !important;
  background: var(--el-color-primary) !important;
}

/* 暗色模式下splitpanes相关元素 */
.dark-theme .splitpanes,
.playground-container.dark-theme .splitpanes,
body.dark-theme .splitpanes,
html.dark .splitpanes {
  background: transparent !important;
}

.dark-theme .splitpanes__pane,
.playground-container.dark-theme .splitpanes__pane,
body.dark-theme .splitpanes__pane,
html.dark .splitpanes__pane {
  background: transparent !important;
}

.dark-theme .default-theme.splitpanes,
.playground-container.dark-theme .default-theme.splitpanes,
body.dark-theme .default-theme.splitpanes,
html.dark .default-theme.splitpanes {
  background: transparent !important;
}

/* ===== 卡片折叠样式 ===== */
.collapsible-card {
  transition: all 0.3s ease;
}

.card-header-with-collapse {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-header-with-collapse span {
  font-weight: 500;
}

/* ===== 折叠过渡动画 ===== */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  max-height: 2000px;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
  margin: 0;
  padding: 0;
}

/* 暗色模式下transition内的所有div */
.dark-theme .result-card .collapse-enter-active,
.dark-theme .result-card .collapse-leave-active,
.dark-theme .test-params-card .collapse-enter-active,
.dark-theme .test-params-card .collapse-leave-active {
  background-color: transparent !important;
}

.dark-theme .result-card .collapse-enter-active > div,
.dark-theme .result-card .collapse-leave-active > div,
.dark-theme .test-params-card .collapse-enter-active > div,
.dark-theme .test-params-card .collapse-leave-active > div {
  background-color: transparent !important;
}

/* 暗色模式下明确处理transition包裹的内容 */
.dark-theme .result-card > div > div,
.dark-theme .result-card .el-card__body > div > div {
  background-color: transparent !important;
}

/* 暗色模式下强制所有嵌套div背景透明 */
.dark-theme .result-card div[v-show],
.dark-theme .result-card .result-content,
.dark-theme .test-params-card div[v-show] {
  background-color: transparent !important;
}

/* 暗色模式下el-card内所有div背景 */
.dark-theme .el-card__body div {
  background-color: transparent;
}

/* 暗色模式下特殊元素保持深色背景 */
.dark-theme .result-debug-box,
.dark-theme .console-container,
.dark-theme pre,
.dark-theme .stack-trace pre {
  background-color: #1a1a1a !important;
}

/* 暗色模式下Alert组件不要透明 */
.dark-theme .el-alert {
  background-color: rgba(255, 255, 255, 0.05) !important;
}

/* 暗色模式下vue transition元素 */
.dark-theme .collapse-enter-from,
.dark-theme .collapse-leave-to {
  background-color: transparent !important;
}

/* 暗色模式下所有可能的白色背景元素 - 最强覆盖 */
.dark-theme .playground-card div,
.dark-theme .playground-card .el-card div {
  background: transparent;
}

/* 暗色模式下必须保持背景的元素 */
.dark-theme .playground-card,
.dark-theme .test-params-card,
.dark-theme .result-card,
.dark-theme .help-card,
.dark-theme .console-card {
  background: #1f1f1f !important;
}

.dark-theme .el-card__header {
  background: #252525 !important;
}

.dark-theme .el-card__body {
  background: #1f1f1f !important;
}

/* 暗色模式下v-show控制的div */
.dark-theme [v-show] {
  background: transparent !important;
}

/* 暗色模式下transition组件的div */
.dark-theme .el-collapse-transition,
.dark-theme [style*="max-height"] {
  background: transparent !important;
}

/* 暗色模式下确保没有白色背景的通用规则 */
.dark-theme * {
  scrollbar-color: #3c3c3c #1a1a1a;
}

/* 暗色模式下强制覆盖可能的白色背景 */
.dark-theme .test-section > div,
.dark-theme .test-section > div > div {
  background-color: transparent !important;
}

/* 暗色模式下分隔线和面板的全局样式 */
.dark-theme .splitpanes.default-theme .splitpanes__splitter {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.dark-theme .splitpanes.default-theme .splitpanes__splitter::before,
.dark-theme .splitpanes.default-theme .splitpanes__splitter::after {
  background-color: rgba(255, 255, 255, 0.08) !important;
}

.dark-theme .editor-pane,
.dark-theme .test-pane {
  background: transparent !important;
}

/* 确保编辑器面板和测试面板高度一致 */
.editor-pane,
.test-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 暗色模式下确保所有分隔线都是深色 - 终极覆盖 */
.dark-theme .splitpanes__splitter,
.dark-theme .splitpanes.default-theme > .splitpanes__splitter,
.dark-theme .splitpanes--vertical > .splitpanes__splitter,
.playground-container.dark-theme .splitpanes__splitter,
.playground-container.dark-theme .splitpanes.default-theme > .splitpanes__splitter,
.playground-container.dark-theme .splitpanes--vertical > .splitpanes__splitter,
body.dark-theme .playground-container .splitpanes__splitter,
html.dark .playground-container .splitpanes__splitter,
.splitpanes.default-theme .dark-theme .splitpanes__splitter,
div.dark-theme .splitpanes__splitter {
  background-color: rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.08) !important;
  border: none !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
}

/* 暗色模式下分隔线hover效果 */
.dark-theme .splitpanes__splitter:hover,
.dark-theme .splitpanes.default-theme > .splitpanes__splitter:hover,
.playground-container.dark-theme .splitpanes__splitter:hover,
body.dark-theme .playground-container .splitpanes__splitter:hover,
html.dark .playground-container .splitpanes__splitter:hover {
  background-color: var(--el-color-primary) !important;
  background: var(--el-color-primary) !important;
}

/* ===== 优化的右侧面板折叠按钮 ===== */
.panel-collapse-btn {
  position: absolute;
  top: 50%;
  left: -20px;
  transform: translateY(-50%);
  width: 20px;
  height: 80px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  border-radius: 10px 0 0 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: white;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.1);
}

.panel-collapse-btn:hover {
  left: -22px;
  width: 24px;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.15);
  background: linear-gradient(135deg, var(--el-color-primary-light-3) 0%, var(--el-color-primary) 100%);
}

.panel-collapse-btn:active {
  transform: translateY(-50%) scale(0.95);
}

.panel-collapse-btn .el-icon {
  font-size: 14px;
  transition: transform 0.3s ease;
}

.panel-collapse-btn:hover .el-icon {
  transform: scale(1.2);
}

.panel-expand-btn {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 120px;
  background: linear-gradient(180deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  border-radius: 8px 0 0 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 100;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: white;
  gap: 8px;
  box-shadow: -3px 0 10px rgba(0, 0, 0, 0.15);
  padding: 15px 0;
}

.panel-expand-btn:hover {
  width: 36px;
  right: -2px;
  box-shadow: -6px 0 16px rgba(0, 0, 0, 0.2);
  background: linear-gradient(180deg, var(--el-color-primary-light-3) 0%, var(--el-color-primary) 100%);
}

.panel-expand-btn:active {
  transform: translateY(-50%) scale(0.98);
}

.panel-expand-btn .el-icon {
  font-size: 18px;
  transition: transform 0.3s ease;
}

.panel-expand-btn:hover .el-icon {
  transform: scale(1.15);
}

/* ===== 控制台展开按钮 ===== */
.console-expand-btn {
  position: fixed;
  bottom: 20px;
  right: 20px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  color: white;
  padding: 10px 20px;
  border-radius: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 100;
  font-size: 14px;
  font-weight: 500;
}

.console-expand-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  background: linear-gradient(135deg, var(--el-color-primary-light-3) 0%, var(--el-color-primary) 100%);
}

.console-expand-btn:active {
  transform: translateY(0);
}

/* ===== 按钮动画 ===== */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.9);
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* ===== 文件标签页 ===== */
.file-tabs-container {
  margin-bottom: 12px;
  position: relative;
}

.file-tabs-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-tabs {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

/* 标签页滚动支持 */
.file-tabs :deep(.el-tabs__nav-wrap) {
  overflow: hidden;
}

.file-tabs :deep(.el-tabs__nav-scroll) {
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color) transparent;
  scroll-behavior: smooth; /* 平滑滚动 */
  -webkit-overflow-scrolling: touch; /* iOS 弹性滚动 */
}

.file-tabs :deep(.el-tabs__nav-scroll)::-webkit-scrollbar {
  height: 4px;
}

.file-tabs :deep(.el-tabs__nav-scroll)::-webkit-scrollbar-thumb {
  background-color: var(--el-border-color);
  border-radius: 2px;
}

.file-tabs :deep(.el-tabs__nav-scroll)::-webkit-scrollbar-track {
  background: transparent;
}

.file-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.file-tabs :deep(.el-tabs__item) {
  padding: 0 15px;
  height: 32px;
  line-height: 32px;
  font-size: 13px;
  background-color: transparent;
  border-color: var(--el-border-color);
}

/* 标签内文本样式 */
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  user-select: none;
}

.tab-pinned {
  font-weight: 500;
}

.pin-icon {
  font-size: 12px;
  color: var(--el-color-warning);
}

/* 非活动标签页样式 */
.file-tabs :deep(.el-tabs__item:not(.is-active)) {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.file-tabs :deep(.el-tabs__item:not(.is-active):hover) {
  background-color: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

/* 活动标签页样式 */
.file-tabs :deep(.el-tabs__item.is-active) {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
}

/* 暗色模式非活动标签 */
.dark-theme .file-tabs :deep(.el-tabs__item:not(.is-active)) {
  background-color: rgba(255, 255, 255, 0.04);
  color: var(--el-text-color-secondary);
}

.dark-theme .file-tabs :deep(.el-tabs__item:not(.is-active):hover) {
  background-color: rgba(255, 255, 255, 0.08);
  color: var(--el-text-color-primary);
}

/* 暗色模式活动标签 */
.dark-theme .file-tabs :deep(.el-tabs__item.is-active) {
  background-color: rgba(64, 158, 255, 0.2);
  color: var(--el-color-primary);
  font-weight: 500;
}

/* ===== 右键菜单样式 ===== */
.tab-context-menu {
  position: fixed;
  z-index: 9999;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  padding: 6px 0;
  min-width: 160px;
}

.dark-theme .tab-context-menu {
  background: #2a2a2a !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4) !important;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-primary);
  transition: background-color 0.2s;
}

.context-menu-item:hover {
  background-color: var(--el-fill-color-light);
}

.dark-theme .context-menu-item {
  color: rgba(255, 255, 255, 0.85) !important;
}

.dark-theme .context-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
}

.context-menu-item.disabled {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
  pointer-events: none;
}

.context-menu-item .el-icon {
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.context-menu-divider {
  height: 1px;
  background-color: var(--el-border-color-lighter);
  margin: 6px 0;
}

.dark-theme .context-menu-divider {
  background-color: rgba(255, 255, 255, 0.08);
}

.new-file-tab-btn {
  flex-shrink: 0;
}

/* ===== 编辑器区域 ===== */
.editor-section {
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 原生编辑器样式 */
.native-editor {
  width: 100%;
  height: 100%;
  padding: 12px;
  border: none;
  outline: none;
  resize: none;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  tab-size: 2;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
}

.native-editor::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.native-editor::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
}

.native-editor::-webkit-scrollbar-thumb {
  background: var(--el-fill-color-dark);
  border-radius: 5px;
}

.native-editor::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-darker);
}

/* ===== 测试区域 ===== */
.test-section {
  height: 100%;
  overflow-y: auto;
  padding-right: 5px;
  position: relative;
}

.test-section::-webkit-scrollbar {
  width: 6px;
}

.test-section::-webkit-scrollbar-thumb {
  background: var(--el-border-color-darker);
  border-radius: 3px;
}

.test-section::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color);
}

.test-params-card,
.result-card,
.help-card {
  margin-bottom: 10px;
  transition: all 0.3s ease;
}

.test-params-card :deep(.el-card__body) {
  padding: 12px;
}

.dark-theme .test-params-card,
.dark-theme .result-card,
.dark-theme .help-card {
  background: #1f1f1f;
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .test-params-card :deep(.el-card__header),
.dark-theme .result-card :deep(.el-card__header),
.dark-theme .help-card :deep(.el-card__header) {
  background: #252525;
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

/* 测试参数表单布局 */
.test-params-form {
  padding: 0;
}

.test-params-form :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.test-params-form .share-url-item {
  margin-bottom: 10px;
}

.test-params-form .password-item {
  margin-bottom: 10px;
}

.test-params-form .method-item-horizontal {
  margin-bottom: 10px;
}

.test-params-form .method-item-horizontal :deep(.el-radio-group) {
  display: flex;
  flex-direction: row;
  gap: 6px;
  flex-wrap: nowrap;
  width: 100%;
}

.test-params-form .method-item-horizontal :deep(.el-radio) {
  margin-right: 0;
  white-space: nowrap;
  flex: 0 0 auto;
}

.test-params-form .method-item-horizontal :deep(.el-radio__label) {
  padding-left: 6px;
  font-size: 13px;
}

.test-params-form .button-item {
  margin-bottom: 0;
}

/* 暗色模式下的表单样式修复 */
.dark-theme .test-params-card :deep(.el-form-item__label),
.dark-theme .result-card :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .test-params-card :deep(.el-input__wrapper) {
  background-color: #1a1a1a;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
}

.dark-theme .test-params-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.25) inset;
}

.dark-theme .test-params-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.dark-theme .test-params-card :deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.85);
  background-color: transparent;
}

.dark-theme .test-params-card :deep(.el-radio__label) {
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .test-params-card :deep(.el-radio__inner) {
  background-color: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.3);
}

.dark-theme .test-params-card :deep(.el-radio__input.is-checked .el-radio__inner) {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.dark-theme .test-params-card :deep(.el-radio:hover .el-radio__inner) {
  border-color: var(--el-color-primary);
}

.editor-section {
  margin-bottom: 20px;
}

.test-section {
  display: flex;
  flex-direction: column;
}

.test-params-card,
.result-card {
  width: 100%;
}

.result-content {
  max-height: 500px;
  overflow-y: auto;
}

.dark-theme .result-content {
  background-color: transparent;
  color: rgba(255, 255, 255, 0.85);
}

.result-section {
  margin-bottom: 15px;
  background-color: transparent;
}

.dark-theme .result-section {
  background-color: transparent !important;
}

.section-title {
  font-weight: bold;
  margin-bottom: 8px;
  color: #606266;
}

.result-debug-box {
  margin-bottom: 10px;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 4px;
  word-break: break-all;
}

.dark-theme .result-debug-box {
  background: #1a1a1a;
  color: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.dark-theme .result-debug-box strong {
  color: rgba(255, 255, 255, 0.9);
}

.logs-container {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
  background-color: #f5f7fa;
}

.log-entry {
  display: flex;
  align-items: center;
  padding: 4px 0;
  font-size: 12px;
  font-family: 'Courier New', monospace;
}

.log-time {
  color: #909399;
  margin-right: 8px;
  min-width: 80px;
}

.log-level {
  font-weight: bold;
  margin-right: 8px;
  min-width: 50px;
}

.log-debug .log-level {
  color: #909399;
}

.log-info .log-level {
  color: #409eff;
}

.log-warn .log-level {
  color: #e6a23c;
}

.log-error .log-level {
  color: #f56c6c;
}

.log-message {
  flex: 1;
  word-break: break-all;
}

.stack-trace {
  margin-top: 10px;
}

.stack-trace pre {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}

.dark-theme .stack-trace :deep(.el-collapse) {
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .stack-trace :deep(.el-collapse-item__header) {
  background-color: #1a1a1a;
  color: rgba(255, 255, 255, 0.85);
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .stack-trace :deep(.el-collapse-item__wrap) {
  background-color: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .stack-trace :deep(.el-collapse-item__content) {
  color: rgba(255, 255, 255, 0.85);
}

/* ===== 新建文件对话框样式 ===== */
.new-file-dialog .form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
}

/* 开发语言选择样式 */
.language-radio-group {
  display: flex;
  gap: 20px;
}

.language-radio {
  display: flex;
  align-items: center;
}

.language-radio :deep(.el-radio__label) {
  display: flex;
  align-items: center;
  gap: 6px;
}

.language-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.language-icon.js-icon {
  background: linear-gradient(135deg, #f7df1e 0%, #e6c700 100%);
  color: #323330;
}

.language-icon.py-icon {
  background: linear-gradient(135deg, #3776ab 0%, #ffd43b 100%);
  font-size: 14px;
}

.language-name {
  font-size: 14px;
}

.empty-result {
  text-align: center;
  padding: 40px 0;
  background-color: transparent;
}

.dark-theme .empty-result {
  background-color: transparent;
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .logs-container {
  background-color: #1e1e1e;
  border-color: #3c3c3c;
  color: #d4d4d4;
}

.dark-theme .stack-trace pre {
  background-color: #1e1e1e;
  color: #d4d4d4;
}

.dark-theme .section-title {
  color: rgba(255, 255, 255, 0.9);
}

.console-card {
  margin-top: 12px;
}

.console-container {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
  background-color: #fafafa;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 13px;
  transition: all 0.3s ease;
  scroll-behavior: smooth; /* 平滑滚动 */
  -webkit-overflow-scrolling: touch; /* iOS 弹性滚动 */
}

.dark-theme .console-container {
  background-color: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.85);
}

.console-entry {
  display: flex;
  align-items: flex-start;
  padding: 8px 10px;
  margin: 4px 0;
  line-height: 1.6;
  word-break: break-all;
  border-radius: 4px;
  border-left: 3px solid transparent;
  transition: all 0.2s ease;
}

.console-entry:hover {
  background: rgba(0, 0, 0, 0.03);
}

.dark-theme .console-entry:hover {
  background: rgba(255, 255, 255, 0.05);
}

.console-time {
  color: var(--el-text-color-secondary);
  margin-right: 10px;
  min-width: 80px;
  flex-shrink: 0;
  font-size: 11px;
  opacity: 0.8;
}

.console-level {
  font-weight: 600;
  margin-right: 10px;
  min-width: 50px;
  flex-shrink: 0;
  font-size: 12px;
}

.console-debug {
  border-left-color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
}

.console-debug .console-level {
  color: var(--el-text-color-secondary);
}

.console-info {
  border-left-color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.console-info .console-level {
  color: var(--el-color-info);
}

.console-warn {
  border-left-color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.console-warn .console-level {
  color: var(--el-color-warning);
}

.console-error {
  border-left-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.console-error .console-level {
  color: var(--el-color-danger);
}

/* JavaScript 日志样式（绿色主题） */
.console-js-source {
  border-left-color: var(--el-color-success) !important;
  background: var(--el-color-success-light-9) !important;
}

.console-java-source {
  border-left-color: var(--el-color-warning) !important;
  background: var(--el-color-warning-light-9) !important;
}

.console-python-source {
  border-left-color: var(--el-color-info) !important;
  background: var(--el-color-info-light-9) !important;
}

.dark-theme .console-js-source {
  background: rgba(103, 194, 58, 0.15) !important;
}

.dark-theme .console-java-source {
  background: rgba(230, 162, 60, 0.15) !important;
}

.dark-theme .console-python-source {
  background: rgba(64, 158, 255, 0.15) !important;
}

.console-source-tag {
  display: inline-block;
  color: white;
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 10px;
  margin-right: 8px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.console-source-JS {
  background: linear-gradient(135deg, var(--el-color-success) 0%, var(--el-color-success-light-3) 100%);
  box-shadow: 0 2px 4px rgba(103, 194, 58, 0.3);
}

.console-source-java {
  background: linear-gradient(135deg, var(--el-color-warning) 0%, var(--el-color-warning-light-3) 100%);
  box-shadow: 0 2px 4px rgba(230, 162, 60, 0.3);
}

.console-source-python {
  background: linear-gradient(135deg, var(--el-color-info) 0%, var(--el-color-info-light-3) 100%);
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.3);
}

.console-message {
  flex: 1;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.empty-console {
  text-align: center;
  color: var(--el-text-color-placeholder);
  padding: 40px 0;
  font-size: 14px;
}

.help-content {
  text-align: left;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.help-content h3 {
  margin-top: 20px;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.help-content h4 {
  margin-top: 15px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.help-content ul,
.help-content ol {
  margin: 10px 0;
  padding-left: 24px;
}

.help-content li {
  margin: 8px 0;
  line-height: 1.7;
}

.help-content code {
  background: var(--el-fill-color);
  padding: 3px 8px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 0.9em;
  color: var(--el-color-danger);
  border: 1px solid var(--el-border-color-lighter);
}

.help-content pre {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  border: 1px solid var(--el-border-color);
  line-height: 1.6;
  margin: 10px 0;
}

.help-content a {
  color: var(--el-color-primary);
  text-decoration: none;
  transition: all 0.3s;
  font-weight: 500;
}

.help-content a:hover {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

.help-content p {
  margin: 10px 0;
  line-height: 1.7;
}

.parser-list-section {
  margin-top: 20px;
}

/* ===== 响应式布局 ===== */
/* 移动端布局：内容自然向下流动 */
.mobile-layout {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.mobile-layout .editor-section {
  width: 100%;
  margin: 0;
  margin-bottom: 0;
  padding: 0;
  position: relative;
}

/* 编辑器拖拽调整高度手柄 */
.editor-resize-handle {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 20px;
  cursor: ns-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(to bottom, transparent, var(--el-fill-color-light));
  z-index: 100;
  touch-action: none;
}

.editor-resize-handle:hover,
.editor-resize-handle:active {
  background: linear-gradient(to bottom, transparent, var(--el-color-primary-light-8));
}

.resize-handle-bar {
  width: 50px;
  height: 4px;
  background: var(--el-border-color);
  border-radius: 2px;
  transition: all 0.2s;
}

.editor-resize-handle:hover .resize-handle-bar,
.editor-resize-handle:active .resize-handle-bar {
  background: var(--el-color-primary);
  width: 70px;
}

/* 暗色主题下的拖拽横条 */
.dark-theme .editor-resize-handle {
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.05)) !important;
}

.dark-theme .editor-resize-handle:hover,
.dark-theme .editor-resize-handle:active {
  background: linear-gradient(to bottom, transparent, rgba(64, 158, 255, 0.2)) !important;
}

.dark-theme .resize-handle-bar {
  background: rgba(255, 255, 255, 0.3) !important;
}

.dark-theme .editor-resize-handle:hover .resize-handle-bar,
.dark-theme .editor-resize-handle:active .resize-handle-bar {
  background: var(--el-color-primary) !important;
  width: 70px;
}

/* 移动端编辑器容器：去掉所有边距 */
.playground-container.is-mobile .mobile-layout .editor-section {
  margin: 0;
  padding: 0;
  position: relative;
}

.playground-container.is-mobile .mobile-layout .editor-section :deep(.monaco-editor-container) {
  border-radius: 0;
  border-left: none;
  border-right: none;
}

/* 移动端编辑器悬浮操作按钮 - 固定定位 */
.mobile-editor-actions {
  position: fixed;
  bottom: 150px;
  right: 16px;
  z-index: 1500;
  display: flex;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border-radius: 16px;
  padding: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.mobile-editor-actions .el-button-group {
  display: flex;
  gap: 2px;
}

.mobile-editor-actions .el-button {
  margin: 0;
}

/* 暗色主题下的移动端悬浮按钮 */
.dark-theme .mobile-editor-actions {
  background: rgba(30, 30, 30, 0.95);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
}

/* 移动端编辑器高度优化 */
@media screen and (max-width: 768px) {
  .mobile-layout .editor-section {
    position: relative;
    min-height: 300px;
    max-height: calc(100vh - 180px);
  }
  
  .mobile-layout .editor-section :deep(.monaco-editor-container) {
    border-radius: 0 !important;
  }
  
  .mobile-layout .editor-section :deep(.monaco-editor) {
    /* 不需要设置最小高度 */
  }
  
  /* 编辑器滚动区域底部留白 */
  .mobile-layout .editor-section :deep(.monaco-scrollable-element) {
    padding-bottom: 10px !important;
  }
}

/* 移动端代码问题浮窗按钮 */
.mobile-problems-btn {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
  background: linear-gradient(135deg, #f56c6c 0%, #ff8787 100%);
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.4);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: white;
}

.mobile-problems-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.5);
}

.mobile-problems-btn:active {
  transform: scale(0.95);
}

.mobile-problems-btn .problems-badge :deep(.el-badge__content) {
  background-color: #fff;
  color: #f56c6c;
  border: 2px solid #f56c6c;
}

.dark-theme .mobile-problems-btn {
  background: linear-gradient(135deg, #c45656 0%, #d66b6b 100%);
  box-shadow: 0 4px 12px rgba(196, 86, 86, 0.5);
}

.mobile-editor-actions .editor-action-btn {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
}

.mobile-editor-actions .editor-action-btn:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  transform: translateY(-2px);
  transition: all 0.2s ease;
}

.dark-theme .mobile-editor-actions .editor-action-btn {
  background: rgba(30, 30, 30, 0.95);
  border-color: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .mobile-editor-actions .editor-action-btn:hover {
  background: rgba(40, 40, 40, 0.95);
  border-color: rgba(255, 255, 255, 0.2);
}

/* 移动端当前行问题提示 - 浅色系 */
.mobile-current-problem {
  position: fixed;
  bottom: 80px;
  left: 12px;
  right: 12px;
  background: #fee2e2;
  color: #1f2937;
  padding: 12px 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 2000;
  border-radius: 8px;
  border-left: 4px solid #dc2626;
  border-right: 1px solid #fca5a5;
  border-top: 1px solid #fca5a5;
  border-bottom: 1px solid #fca5a5;
}

.mobile-current-problem .problem-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 14px;
  color: #1f2937;
}

.mobile-current-problem .error-icon {
  color: #dc2626;
  font-size: 18px;
}

.mobile-current-problem .warning-icon {
  color: #ea580c;
  font-size: 18px;
}

.mobile-current-problem .problem-title {
  flex: 1;
  color: #374151;
}

.mobile-current-problem .close-icon {
  font-size: 18px;
  cursor: pointer;
  color: #6b7280;
  transition: color 0.2s;
}

.mobile-current-problem .close-icon:hover {
  color: #1f2937;
}

.mobile-current-problem .problem-message {
  font-size: 13px;
  line-height: 1.6;
  color: #4b5563;
  word-break: break-word;
}

/* 警告类型的问题 - 浅橙色 */
.mobile-current-problem.warning {
  background: #fed7aa;
  border-left-color: #ea580c;
  border-right-color: #fdba74;
  border-top-color: #fdba74;
  border-bottom-color: #fdba74;
}

.mobile-current-problem.warning .problem-header,
.mobile-current-problem.warning .problem-title {
  color: #9a3412;
}

.mobile-current-problem.warning .problem-message {
  color: #7c2d12;
}

/* 暗色主题下的移动端错误提示框 */
.dark-theme .mobile-current-problem {
  background: #7f1d1d !important;
  border-left-color: #dc2626 !important;
  border-right-color: #991b1b !important;
  border-top-color: #991b1b !important;
  border-bottom-color: #991b1b !important;
}

.dark-theme .mobile-current-problem .problem-header,
.dark-theme .mobile-current-problem .problem-title,
.dark-theme .mobile-current-problem .problem-message {
  color: #fecaca !important;
}

.dark-theme .mobile-current-problem .error-icon {
  color: #fca5a5 !important;
}

.dark-theme .mobile-current-problem .warning-icon {
  color: #fdba74 !important;
}

.dark-theme .mobile-current-problem .close-icon {
  color: #fca5a5 !important;
}

.dark-theme .mobile-current-problem .close-icon:hover {
  color: #fecaca !important;
}

/* 暗色主题下的警告 - 使用明显的橙黄色 */
.dark-theme .mobile-current-problem.warning {
  background: #854d0e !important;
  border-left-color: #f59e0b !important;
  border-right-color: #a16207 !important;
  border-top-color: #a16207 !important;
  border-bottom-color: #a16207 !important;
}

.dark-theme .mobile-current-problem.warning .problem-header,
.dark-theme .mobile-current-problem.warning .problem-title,
.dark-theme .mobile-current-problem.warning .problem-message {
  color: #fde047 !important;
}

.dark-theme .mobile-current-problem.warning .warning-icon {
  color: #fbbf24 !important;
}

.dark-theme .mobile-current-problem.warning .close-icon {
  color: #fde047 !important;
}

.dark-theme .mobile-current-problem.warning .close-icon:hover {
  color: #fef3c7 !important;
}

/* 暗色主题下的全局弹出层和下拉框背景修复 */
.dark-theme :deep(.el-popper),
.dark-theme :deep(.el-select-dropdown),
.dark-theme :deep(.el-autocomplete-suggestion),
.dark-theme :deep(.el-dropdown-menu),
.dark-theme :deep(.el-tooltip__popper) {
  background: #1f1f1f !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

.dark-theme :deep(.el-popper.is-light),
.dark-theme :deep(.el-tooltip__popper.is-light) {
  background: #2a2a2a !important;
  border-color: rgba(255, 255, 255, 0.15) !important;
}

.dark-theme :deep(.el-select-dropdown__item),
.dark-theme :deep(.el-autocomplete-suggestion__list li),
.dark-theme :deep(.el-dropdown-menu__item) {
  color: rgba(255, 255, 255, 0.85) !important;
}

.dark-theme :deep(.el-select-dropdown__item:hover),
.dark-theme :deep(.el-autocomplete-suggestion__list li:hover),
.dark-theme :deep(.el-dropdown-menu__item:hover) {
  background: rgba(255, 255, 255, 0.1) !important;
}

/* 代码问题对话框样式 */
.problems-dialog .problems-list {
  max-height: 60vh;
  overflow-y: auto;
}

.problems-dialog .el-alert {
  cursor: pointer;
  transition: all 0.2s ease;
}

.problems-dialog .el-alert:hover {
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 桌面端代码问题列表 */
.problems-list-desktop {
  max-height: 300px;
  overflow-y: auto;
}

.problem-item {
  padding: 10px 12px;
  border-left: 3px solid;
  margin-bottom: 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--el-fill-color-lighter);
}

.problem-item:hover {
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.problem-error {
  border-left-color: #f56c6c;
  background: rgba(245, 108, 108, 0.05);
}

.problem-warning {
  border-left-color: #e6a23c;
  background: rgba(230, 162, 60, 0.05);
}

.problem-info {
  border-left-color: #909399;
  background: rgba(144, 147, 153, 0.05);
}

.problem-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-weight: 500;
}

.problem-error .problem-header {
  color: #f56c6c;
}

.problem-warning .problem-header {
  color: #e6a23c;
}

.problem-info .problem-header {
  color: #909399;
}

.problem-line {
  font-size: 13px;
}

.problem-message {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-left: 24px;
  line-height: 1.5;
}

.dark-theme .problem-item {
  background: rgba(255, 255, 255, 0.03);
}

.dark-theme .problem-error {
  background: rgba(245, 108, 108, 0.1);
}

.dark-theme .problem-warning {
  background: rgba(230, 162, 60, 0.1);
}

.dark-theme .problem-info {
  background: rgba(144, 147, 153, 0.1);
}

.mobile-test-section {
  width: 100%;
  height: auto !important;
  overflow-y: visible !important;
  padding: 0;
}

.mobile-test-section .test-params-card,
.mobile-test-section .result-card {
  margin-bottom: 12px;
}

.playground-container.is-mobile .playground-loading-card {
  width: 90%;
  max-width: 320px;
  padding: 24px 30px;
}

.playground-container.is-mobile .playground-auth-card {
  width: 90%;
  max-width: 400px;
  padding: 30px 24px;
}

@media screen and (max-width: 1200px) {
  .splitpanes {
    min-height: 400px;
  }
  
  .header-actions {
    flex-wrap: wrap;
  }
  
  .console-container {
    max-height: 250px;
  }
  
  .empty-console {
    padding: 20px 0;
  }
}

@media screen and (max-width: 768px) {
  .playground-container {
    padding: 10px;
  }
  
  .card-header {
    flex-direction: column;
    align-items: stretch;
    gap: 4px;
    padding: 4px 8px !important;
    margin: 0 !important;
  }
  
  /* 移动端两排按钮布局 */
  .header-actions.mobile-two-rows {
    display: flex;
    flex-direction: column;
    gap: 3px;
    width: 100%;
    margin: 0 !important;
    padding: 0 !important;
  }
  
  .header-actions.mobile-two-rows .action-row {
    display: flex;
    width: 100%;
    margin: 0;
  }
  
  .header-actions.mobile-two-rows .el-button-group {
    flex: 1;
    display: flex;
    width: 100%;
  }
  
  .header-actions.mobile-two-rows .el-button-group .el-button {
    flex: 1;
    min-width: 0;
    padding: 6px 4px !important;
    font-size: 12px !important;
    margin: 0 !important;
  }
  
  /* 隐藏移动端不必要的元素（主题切换、全屏、更多按钮） */
  .header-actions.mobile-two-rows > .el-dropdown,
  .header-actions.mobile-two-rows > .el-tooltip,
  .header-actions.mobile-two-rows > .el-button {
    display: none !important;
  }
  
  /* 显示 action-row 内的按钮 */
  .header-actions.mobile-two-rows .action-row .el-button-group,
  .header-actions.mobile-two-rows .action-row .el-tooltip {
    display: flex !important;
  }
  
  .console-container {
    max-height: 300px;
    padding: 8px;
  }
  
  .empty-console {
    padding: 12px 0;
    font-size: 13px;
  }
  
  .console-entry {
    font-size: 12px;
    padding: 6px 0;
  }
  
  .panel-expand-btn {
    right: 10px;
  }
  
  .test-params-form .method-item-horizontal :deep(.el-radio-group) {
    flex-direction: row;
    gap: 4px;
  }
  
  /* 移动端单行测试参数布局 */
  .test-params-form.mobile-single-row {
    padding: 0;
  }
  
  .test-params-form.mobile-single-row .test-params-row {
    display: flex;
    gap: 4px;
    margin-bottom: 4px;
  }
  
  .test-params-form.mobile-single-row .url-input {
    flex: 2;
    min-width: 0;
  }
  
  .test-params-form.mobile-single-row .pwd-input {
    flex: 1;
    min-width: 60px;
  }
  
  .test-params-form.mobile-single-row .method-radio {
    flex: 1;
    display: flex;
    gap: 4px;
  }
  
  .test-params-form.mobile-single-row .method-radio :deep(.el-radio) {
    margin-right: 0;
    flex: 1;
  }
  
  .test-params-form.mobile-single-row .method-radio :deep(.el-radio__label) {
    padding-left: 4px;
    font-size: 11px;
  }
  
  .test-params-form.mobile-single-row .test-button {
    flex: 0 0 auto;
    min-width: 70px;
  }
  
  /* 减小测试参数卡片边距 */
  .mobile-test-section .test-params-card {
    margin-top: 4px !important;
  }
  
  .mobile-test-section .test-params-card :deep(.el-card__body) {
    padding: 8px !important;
  }
  
  /* 移动端结果区域自适应高度 */
  .result-content {
    max-height: none !important;
    overflow-y: visible !important;
  }
  
  /* 移动端测试区域不使用固定高度 */
  .test-section {
    height: auto !important;
    overflow-y: visible !important;
  }
  
  /* 移动端对话框样式 */
  .publish-dialog :deep(.el-dialog) {
    margin: 5vh auto !important;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
  }
  
  .publish-dialog :deep(.el-dialog__body) {
    flex: 1;
    overflow-y: auto;
    padding: 15px;
  }
  
  .publish-code-textarea :deep(.el-textarea__inner) {
    font-size: 12px;
    font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  }
  
  .publish-checklist {
    font-size: 13px;
  }
  
  .publish-checklist ul {
    line-height: 1.8;
  }
  
  .publish-checklist li {
    margin-bottom: 4px;
  }
  
  .dialog-footer-mobile {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    flex-wrap: wrap;
  }
  
  .shortcuts-dialog :deep(.el-dialog) {
    margin: 5vh auto !important;
    max-height: 90vh;
  }
  
  .shortcuts-dialog :deep(.el-dialog__body) {
    padding: 15px;
    max-height: calc(90vh - 120px);
    overflow-y: auto;
  }
  
  .shortcuts-table :deep(.el-table__body) {
    font-size: 13px;
  }
}

/* ===== 改进的滚动条样式 ===== */
.test-section::-webkit-scrollbar,
.console-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.test-section::-webkit-scrollbar-track,
.console-container::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.test-section::-webkit-scrollbar-thumb,
.console-container::-webkit-scrollbar-thumb {
  background: var(--el-border-color-darker);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.test-section::-webkit-scrollbar-thumb:hover,
.console-container::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-extra-light);
}

/* 移动端平滑滚动优化 */
@media screen and (max-width: 768px) {
  .test-section,
  .console-container,
  .result-content,
  .file-tabs :deep(.el-tabs__nav-scroll),
  .problems-list,
  .help-content {
    scroll-behavior: smooth !important;
    -webkit-overflow-scrolling: touch !important;
  }
  
  /* 移动端隐藏滚动条，更简洁 */
  .test-section::-webkit-scrollbar,
  .console-container::-webkit-scrollbar {
    display: none;
  }
  
  .test-section,
  .console-container {
    scrollbar-width: none; /* Firefox */
  }
}

/* ===== 暗色主题优化 ===== */
.dark-theme .editor-section {
  border-color: rgba(255, 255, 255, 0.15);
  background: #1a1a1a;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* 暗色主题下的原生编辑器 */
.dark-theme .native-editor {
  background: #1a1a1a;
  color: #d4d4d4;
}

.dark-theme .native-editor::-webkit-scrollbar-track {
  background: #2a2a2a;
}

.dark-theme .native-editor::-webkit-scrollbar-thumb {
  background: #4a4a4a;
}

.dark-theme .native-editor::-webkit-scrollbar-thumb:hover {
  background: #5a5a5a;
}

/* 暗色模式下所有el-card的body部分背景 */
.dark-theme .test-params-card :deep(.el-card__body),
.dark-theme .result-card :deep(.el-card__body),
.dark-theme .help-card :deep(.el-card__body),
.dark-theme .console-card :deep(.el-card__body) {
  background: #1f1f1f !important;
  color: rgba(255, 255, 255, 0.85);
}

/* 暗色模式下所有可能的白色背景容器 */
.dark-theme .result-card :deep(div) {
  background-color: transparent;
}

.dark-theme .result-card :deep(pre) {
  background-color: #1a1a1a;
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .result-card :deep(code) {
  background-color: #252525;
  color: rgba(255, 255, 255, 0.85);
}

/* 强制覆盖所有可能有白色背景的元素 */
.dark-theme .result-card > div,
.dark-theme .result-card .el-card__body > div {
  background-color: transparent !important;
}

/* 暗色模式下的按钮样式修复 */
.dark-theme .test-params-card :deep(.el-button--primary) {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.dark-theme .test-params-card :deep(.el-button--primary:hover) {
  background-color: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

/* 暗色模式下的Alert组件 */
.dark-theme .result-card :deep(.el-alert) {
  background-color: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.dark-theme .result-card :deep(.el-alert.el-alert--success) {
  background-color: rgba(103, 194, 58, 0.15);
  border-color: rgba(103, 194, 58, 0.3);
}

.dark-theme .result-card :deep(.el-alert.el-alert--error) {
  background-color: rgba(245, 108, 108, 0.15);
  border-color: rgba(245, 108, 108, 0.3);
}

.dark-theme .result-card :deep(.el-alert__title) {
  color: rgba(255, 255, 255, 0.9);
}

/* 暗色模式下的Empty组件 */
.dark-theme .result-card :deep(.el-empty__description) {
  color: rgba(255, 255, 255, 0.5);
}

/* 暗色模式下的JsonViewer */
.dark-theme .result-card :deep(.jv-container) {
  background-color: #1a1a1a !important;
}

.dark-theme .result-card :deep(.jv-code) {
  background-color: #1a1a1a !important;
  color: rgba(255, 255, 255, 0.85) !important;
}

.dark-theme .result-card :deep(.jv-container .jv-code) {
  background-color: #1a1a1a !important;
}

.dark-theme .result-card :deep(.json-viewer) {
  background-color: #1a1a1a !important;
}

.dark-theme .result-card :deep(.jv-node) {
  color: rgba(255, 255, 255, 0.85) !important;
}

/* 暗色模式下的调试区域 */
.dark-theme .result-section > div[style*="background"] {
  background: #1a1a1a !important;
  color: rgba(255, 255, 255, 0.85) !important;
}

/* 暗色模式下的执行时间和其他文本 */
.dark-theme .result-section > div {
  color: rgba(255, 255, 255, 0.85);
}

/* 暗色模式下的卡片折叠按钮 */
.dark-theme .card-header-with-collapse .el-button {
  color: rgba(255, 255, 255, 0.85);
}

.dark-theme .card-header-with-collapse .el-button:hover {
  color: var(--el-color-primary);
}

.dark-theme .console-card {
  background: #1f1f1f;
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .console-card :deep(.el-card__header) {
  background: #252525;
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .console-container {
  background: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.15);
}

.dark-theme .console-entry {
  border-left-color: rgba(255, 255, 255, 0.2);
}

.dark-theme .console-entry:hover {
  background: rgba(255, 255, 255, 0.08);
}

.dark-theme .section-title {
  color: rgba(255, 255, 255, 0.9);
}

.dark-theme .console-info {
  background: rgba(64, 158, 255, 0.1);
}

.dark-theme .console-warn {
  background: rgba(230, 162, 60, 0.1);
}

.dark-theme .console-error {
  background: rgba(245, 108, 108, 0.1);
}

.dark-theme .console-debug {
  background: rgba(144, 147, 153, 0.1);
}

/* ===== 动画效果优化 ===== */
.collapsible-card {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.el-button {
  transition: all 0.2s ease;
}

.el-button:active {
  transform: scale(0.95);
}

/* ===== 帮助卡片样式 ===== */
.help-card {
  background: var(--el-fill-color-lighter);
}

.help-card :deep(.el-card__body) {
  max-height: 600px;
  overflow-y: auto;
}

/* ===== 表格样式优化 ===== */
.parser-list-section :deep(.el-table) {
  font-size: 14px;
}

.parser-list-section :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.parser-list-section :deep(.el-table__row:hover) {
  background-color: var(--el-fill-color-light);
}

/* ===== 暗色模式下 Splitpanes 分隔线最终覆盖 ===== */
.dark-theme :deep(.splitpanes__splitter),
.playground-container.dark-theme :deep(.splitpanes__splitter) {
  background-color: rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

.dark-theme :deep(.splitpanes__splitter:hover),
.playground-container.dark-theme :deep(.splitpanes__splitter:hover) {
  background-color: var(--el-color-primary) !important;
  background: var(--el-color-primary) !important;
}

/* 使用属性选择器覆盖可能的内联样式 */
.dark-theme [class*="splitpanes__splitter"],
.playground-container.dark-theme [class*="splitpanes__splitter"] {
  background-color: rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.08) !important;
}
</style>

