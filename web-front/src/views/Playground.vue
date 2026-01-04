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
        <div class="auth-title">脚本解析器演练场</div>
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

    <!-- 原有内容 - 只在已认证时显示 -->
    <el-card v-if="authed && !loading" class="playground-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">脚本解析器演练场</span>
            <!-- 语言显示（仅支持JavaScript） -->
            <span style="margin-left: 15px; color: var(--el-text-color-secondary); font-size: 12px;">
              JavaScript (ES5)
            </span>
          </div>
          <div class="header-actions">
            <!-- 主要操作 -->
            <el-button-group size="small">
              <el-tooltip content="运行测试 (Ctrl+Enter)" placement="bottom">
                <el-button :icon="testing ? 'Loading' : 'CaretRight'" @click="executeTest" :loading="testing">
                  运行
                </el-button>
              </el-tooltip>
              <el-tooltip content="保存代码 (Ctrl+S)" placement="bottom">
                <el-button icon="Document" @click="saveCode">保存</el-button>
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
            
            <!-- 更多操作 -->
            <el-dropdown size="small" style="margin-left: 5px;">
              <el-button size="small" icon="More" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item icon="DocumentAdd" @click="loadTemplate">加载示例 (Ctrl+R)</el-dropdown-item>
                  <el-dropdown-item icon="Delete" @click="clearCode">清空代码</el-dropdown-item>
                  <el-dropdown-item icon="Promotion" @click="publishParser">发布脚本</el-dropdown-item>
                  <el-dropdown-item icon="QuestionFilled" @click="showShortcutsHelp">快捷键 (Ctrl+/)</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 代码编辑标签页 -->
        <el-tab-pane label="代码编辑" name="editor">
          <!-- 移动端：不使用 splitpanes，内容自然向下流动 -->
          <div v-if="isMobile" class="mobile-layout">
            <!-- 编辑器区域 -->
            <div class="editor-section">
              <MonacoEditor
                ref="editorRef"
                v-model="jsCode"
                :theme="editorTheme"
                :height="'400px'"
                :options="editorOptions"
                @change="onCodeChange"
              />
            </div>
            
            <!-- 测试参数和结果区域 -->
            <div v-if="!collapsedPanels.rightPanel" class="test-section mobile-test-section">
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
                    <el-form :model="testParams" label-width="0px" size="small" class="test-params-form">
                      <el-form-item label="" class="share-url-item">
                        <el-input
                          v-model="testParams.shareUrl"
                          placeholder="请输入分享链接"
                          clearable
                        />
                      </el-form-item>
                      <el-form-item label="" class="password-item">
                        <el-input
                          v-model="testParams.pwd"
                          placeholder="密码（可选）"
                          clearable
                        />
                      </el-form-item>
                      <el-form-item label="" class="method-item-horizontal">
                        <el-radio-group v-model="testParams.method" size="small">
                          <el-radio label="parse">parse</el-radio>
                          <el-radio label="parseFileList">parseFileList</el-radio>
                        </el-radio-group>
                      </el-form-item>
                      <el-form-item class="button-item">
                        <el-button
                          type="primary"
                          :loading="testing"
                          @click="executeTest"
                          style="width: 100%"
                        >
                          执行测试
                        </el-button>
                      </el-form-item>
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
                <MonacoEditor
                  ref="editorRef"
                  v-model="jsCode"
                  :theme="editorTheme"
                  :height="'calc(100vh - 330px)'"
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
                <!-- 测试参数 -->
                <el-card class="test-params-card collapsible-card" shadow="never">
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
              <el-form :model="testParams" label-width="0px" size="small" class="test-params-form">
                <el-form-item label="" class="share-url-item">
                  <el-input
                    v-model="testParams.shareUrl"
                    placeholder="请输入分享链接"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="" class="password-item">
                  <el-input
                    v-model="testParams.pwd"
                    placeholder="密码（可选）"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="" class="method-item-horizontal">
                  <el-radio-group v-model="testParams.method" size="small">
                    <el-radio label="parse">parse</el-radio>
                    <el-radio label="parseFileList">parseFileList</el-radio>
                    <!-- <el-radio label="parseById">parseById</el-radio> -->
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="button-item">
                  <el-button
                    type="primary"
                    :loading="testing"
                    @click="executeTest"
                    style="width: 100%"
                  >
                    执行测试
                  </el-button>
                </el-form-item>
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
                  <!-- 调试：直接显示 result -->
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
                log.source === 'JS' ? 'console-js-source' : 'console-java-source'
              ]"
            >
              <span class="console-time">{{ formatTime(log.timestamp) }}</span>
              <span class="console-level">{{ log.level }}</span>
              <span v-if="log.source === 'JS'" class="console-source-tag">[JS]</span>
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
                <h3>什么是脚本解析器演练场？</h3>
                <p>演练场允许您快速编写、测试和发布JavaScript解析脚本，无需重启服务器即可调试和验证解析逻辑。</p>
                
                <h3>快速开始</h3>
                <ol>
                  <li>点击"加载示例"查看示例代码模板</li>
                  <li>修改代码中的解析逻辑</li>
                  <li>输入测试URL和密码，点击"执行测试"验证代码</li>
                  <li>测试通过后，点击"发布脚本"保存到数据库</li>
                </ol>

                <h3>脚本格式要求</h3>
                <ul>
                  <li>必须包含元数据注释块（<code>// ==UserScript== ... // ==/UserScript==</code>）</li>
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
import { playgroundApi } from '@/utils/playgroundApi';
import { configureMonacoTypes, loadTypesFromApi } from '@/utils/monacoTypes';
import JsonViewer from 'vue3-json-viewer';

export default {
  name: 'Playground',
  components: {
    MonacoEditor,
    JsonViewer,
    Splitpanes,
    Pane
  },
  setup() {
    const router = useRouter();
    
    // 语言常量
    const LANGUAGE = {
      JAVASCRIPT: 'JavaScript'
    };

    const editorRef = ref(null);
    const jsCode = ref('');
    
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
      jsCode: ''
    });
    const helpCollapseActive = ref([]); // 默认折叠
    const consoleLogs = ref([]); // 控制台日志
    
    // ===== 新增状态管理 =====
    // 折叠状态
    const collapsedPanels = ref({
      rightPanel: false,      // 右侧整体面板
      testParams: false,      // 测试参数卡片
      testResult: false,      // 测试结果卡片
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
      { name: '快捷键帮助', keys: ['Ctrl+/', 'Cmd+/'] }
    ];
    
    // 分栏大小
    const splitSizes = ref([70, 30]);

    // 示例代码模板
    const exampleCode = `// ==UserScript==
// @name         示例解析器
// @type         example_parser
// @displayName  示例网盘
// @description  使用JavaScript实现的示例解析器
// @match        https?://example\.com/s/(?<KEY>\\w+)
// @author       yourname
// @version      1.0.0
// ==/UserScript==

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    logger.info("开始解析: " + url);
    
    var response = http.get('https://example.com');
    if (!response.isSuccess()) {
        throw new Error("请求失败: " + response.statusCode());
    }
    
    var html = response.body();
    // 这里添加你的解析逻辑
    // 例如：使用正则表达式提取下载链接
    
    return "https://example.com/download/file.zip";
}

/**
 * 解析文件列表（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {Array} 文件信息数组
 */
function parseFileList(shareLinkInfo, http, logger) {
    var dirId = shareLinkInfo.getOtherParam("dirId") || "0";
    logger.info("解析文件列表，目录ID: " + dirId);
    
    // 这里添加你的文件列表解析逻辑
    var fileList = [];
    
    return fileList;
}

/**
 * 根据文件ID获取下载链接（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parseById(shareLinkInfo, http, logger) {
    var paramJson = shareLinkInfo.getOtherParam("paramJson");
    var fileId = paramJson.fileId;
    logger.info("根据ID解析: " + fileId);
    
    // 这里添加你的按ID解析逻辑
    
    return "https://example.com/download?id=" + fileId;
}`;

    // 编辑器主题
    const editorTheme = computed(() => {
      return isDarkMode.value ? 'vs-dark' : 'vs';
    });
    
    // 计算属性：是否需要显示密码输入界面
    const shouldShowAuthUI = computed(() => {
      return !loading.value && !authChecking.value && !authed.value && playgroundEnabled.value;
    });

    // 编辑器配置
    const editorOptions = {
      minimap: { enabled: true },
      scrollBeyondLastLine: false,
      wordWrap: 'on',
      lineNumbers: 'on',
      formatOnPaste: true,
      formatOnType: true,
      tabSize: 2
    };
    
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
        // 加载保存的代码
        const saved = localStorage.getItem('playground_code');
        if (saved) {
          jsCode.value = saved;
        } else {
          // 默认加载示例代码和示例参数
          jsCode.value = exampleCode;
          testParams.value.shareUrl = 'https://example.com/s/abc';
          testParams.value.pwd = '';
          testParams.value.method = 'parse';
        }
        
        setProgress(50, '初始化Monaco Editor类型定义...');
        await initMonacoTypes();
        
        setProgress(80, '加载完成...');
        
        // 加载保存的主题
        const savedTheme = localStorage.getItem('playground_theme');
        if (savedTheme) {
          currentTheme.value = savedTheme;
          const theme = themes.find(t => t.name === savedTheme);
          if (theme) {
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

    // 代码变化处理
    const onCodeChange = (value) => {
      jsCode.value = value;
      // 保存到localStorage
      localStorage.setItem('playground_code', value);
    };

    // 加载示例代码
    const loadTemplate = () => {
      jsCode.value = exampleCode;
      // 重置测试参数为示例链接
      testParams.value.shareUrl = 'https://example.com/s/abc';
      testParams.value.pwd = '';
      testParams.value.method = 'parse';
      // 清空测试结果
      testResult.value = null;
      consoleLogs.value = [];
      ElMessage.success('已加载JavaScript示例代码');
    };

    // 格式化代码
    const formatCode = () => {
      if (editorRef.value && editorRef.value.getEditor) {
        const editor = editorRef.value.getEditor();
        if (editor) {
          editor.getAction('editor.action.formatDocument').run();
        }
      }
    };

    // 保存代码
    const saveCode = () => {
      localStorage.setItem('playground_code', jsCode.value);
      ElMessage.success('代码已保存');
    };

    // 加载代码
    const loadCode = () => {
      const saved = localStorage.getItem('playground_code');
      if (saved) {
        jsCode.value = saved;
        ElMessage.success('代码已加载');
      } else {
        ElMessage.warning('没有保存的代码');
      }
    };

    // 清空代码
    const clearCode = () => {
      jsCode.value = '';
      testResult.value = null;
      compiledES5Code.value = '';
      compileStatus.value = { success: true, errors: [] };
    };

    // 语言切换处理
    // 执行测试
    const executeTest = async () => {
      if (!jsCode.value.trim()) {
        ElMessage.warning('请先输入JavaScript代码');
        return;
      }

      if (!testParams.value.shareUrl.trim()) {
        ElMessage.warning('请输入分享链接');
        return;
      }
      
      // 检查代码中是否包含潜在的危险模式
      const dangerousPatterns = [
        { pattern: /while\s*\(\s*true\s*\)/gi, message: '检测到 while(true) 无限循环' },
        { pattern: /for\s*\(\s*;\s*;\s*\)/gi, message: '检测到 for(;;) 无限循环' },
        { pattern: /for\s*\(\s*var\s+\w+\s*=\s*\d+\s*;\s*true\s*;/gi, message: '检测到可能的无限循环' }
      ];
      
      for (const { pattern, message } of dangerousPatterns) {
        if (pattern.test(jsCode.value)) {
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

      testing.value = true;
      testResult.value = null;
      consoleLogs.value = []; // 清空控制台

      try {
        const result = await playgroundApi.testScript(
          jsCode.value,  // 直接使用JavaScript代码
          testParams.value.shareUrl,
          testParams.value.pwd,
          testParams.value.method
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
      if (!jsCode.value.trim()) {
        ElMessage.warning('请先编写JavaScript代码');
        return;
      }
      publishForm.value.jsCode = jsCode.value;
      publishDialogVisible.value = true;
    };

    // 确认发布
    const confirmPublish = async () => {
      publishing.value = true;
      try {
        const result = await playgroundApi.saveParser(jsCode.value);
        console.log('保存解析器响应:', result);
        // 检查响应格式
        if (result.code === 200 || result.success) {
          // 从响应或代码中提取type信息
          let parserType = '';
          try {
            const typeMatch = jsCode.value.match(/@type\s+(\w+)/);
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

    // 加载解析器到编辑器
    const loadParserToEditor = async (parser) => {
      try {
        const result = await playgroundApi.getParserById(parser.id);
        if (result.code === 200 && result.data) {
          jsCode.value = result.data.jsCode;
          activeTab.value = 'editor';
          ElMessage.success('已加载到编辑器');
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
    
    // 格式化代码 - Shift + Alt + F
    watch(shiftAltF, (pressed) => {
      if (pressed) {
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
      // 初始化移动端检测
      updateIsMobile();
      window.addEventListener('resize', updateIsMobile);
      
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
    });
    
    onUnmounted(() => {
      window.removeEventListener('resize', updateIsMobile);
    });

    return {
      LANGUAGE,
      editorRef,
      jsCode,
      testParams,
      testResult,
      testing,
      isDarkMode,
      editorTheme,
      shouldShowAuthUI,
      editorOptions,
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
      splitSizes,
      playgroundContainer,
      handleResize
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

.dark-theme .playground-card {
  background: #1a1a1a;
  border-color: rgba(255, 255, 255, 0.1);
}

.dark-theme .playground-card :deep(.el-card__header) {
  background: #1f1f1f;
  border-bottom-color: rgba(255, 255, 255, 0.1);
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
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
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

/* ===== 编辑器区域 ===== */
.editor-section {
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
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

.dark-theme .console-js-source {
  background: rgba(103, 194, 58, 0.15) !important;
}

.console-source-tag {
  display: inline-block;
  background: linear-gradient(135deg, var(--el-color-success) 0%, var(--el-color-success-light-3) 100%);
  color: white;
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 10px;
  margin-right: 8px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(103, 194, 58, 0.3);
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
  margin-bottom: 12px;
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
    align-items: flex-start;
    gap: 10px;
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
  
  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
  
  .panel-expand-btn {
    right: 10px;
  }
  
  .test-params-form .method-item-horizontal :deep(.el-radio-group) {
    flex-direction: column;
    gap: 8px;
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

/* ===== 暗色主题优化 ===== */
.dark-theme .editor-section {
  border-color: rgba(255, 255, 255, 0.15);
  background: #1a1a1a;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
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

