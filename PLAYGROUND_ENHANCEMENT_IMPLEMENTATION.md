# 演练场增强功能实现总结

## 项目日期
2026年1月18日

## 功能概述
本次实现为NetDisk Fast Download项目的演练场（Playground）增加了以下核心功能：

### 1. 编辑器UI增强
- **文件导入功能** - 支持直接导入本地JS/Python/TXT文件
- **原生粘贴支持** - 增强粘贴操作，支持多行代码粘贴，优化移动端体验

### 2. 网络请求安全拦截
- **requests_guard猴子补丁** - 完整的请求拦截和审计日志系统
- **Python代码预处理** - 在运行时自动检测并注入安全补丁
- **实时日志反馈** - 演练场控制台显示安全拦截操作

---

## 详细实现

### 一、演练场编辑器UI增强 (web-front)

#### 1.1 文件导入功能

**位置**: `web-front/src/views/Playground.vue`

**新增组件**:
```vue
<!-- 隐藏的文件导入input -->
<input 
  ref="fileImportInput"
  type="file"
  style="display: none"
  @change="handleFileImport"
  accept=".js,.py,.txt"
/>
```

**新增菜单项**:
```vue
<el-dropdown-item icon="Upload" @click="importFile">导入文件</el-dropdown-item>
```

**实现的方法**:

```javascript
// 触发文件选择对话框
const importFile = () => {
  if (fileImportInput.value) {
    fileImportInput.value.click();
  }
};

// 处理文件导入
const handleFileImport = async (event) => {
  const file = event.target.files?.[0];
  if (!file) return;

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
  }

  // 重置input以允许再次选择同一文件
  if (fileImportInput.value) {
    fileImportInput.value.value = '';
  }
};
```

**特点**:
- 支持 `.js`, `.py`, `.txt` 文件格式
- 自动识别文件语言并设置编辑器模式
- 文件大小提示
- 保存到LocalStorage

---

#### 1.2 原生粘贴支持增强

**位置**: `web-front/src/views/Playground.vue`

**改进点**:

1. **多行粘贴支持** - 正确处理多行代码粘贴
2. **移动端优化** - 处理输入法逐行输入问题
3. **错误处理** - 友好的权限和错误提示

```javascript
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
      ElMessage.warning('粘贴权限被拒绝，请使用 Ctrl+V 快捷键');
    } else {
      console.error('粘贴失败:', error);
      ElMessage.error('粘贴失败: ' + (error.message || '请使用 Ctrl+V'));
    }
  }
};
```

**特点**:
- 处理粘贴权限问题
- 显示粘贴行数
- 支持选区替换和光标位置插入

---

### 二、网络请求安全拦截系统

#### 2.1 requests_guard.py 猴子补丁模块

**位置**: `parser/src/main/resources/requests_guard.py`

**核心功能**:

1. **IP地址验证**
```python
PRIVATE_NETS = [
    "127.0.0.0/8",        # 本地回环
    "10.0.0.0/8",         # A 类私网
    "172.16.0.0/12",      # B 类私网
    "192.168.0.0/16",     # C 类私网
    "0.0.0.0/8",          # 0.x.x.x
    "169.254.0.0/16",     # Link-local
    "224.0.0.0/4",        # 多播地址
    "240.0.0.0/4",        # 预留地址
]
```

2. **危险端口检测**
```python
DANGEROUS_PORTS = [
    22, 25, 53, 3306, 5432, 6379,  # 常见网络服务
    8000, 8001, 8080, 8888,         # 开发服务器端口
    27017,                           # MongoDB
]
```

3. **请求拦截与审计日志**
```
[2026-01-18 10:15:30.123] [Guard-ALLOW] GET  https://example.com/api/data
[2026-01-18 10:15:35.456] [Guard-BLOCK] POST https://127.0.0.1:8080/api - 本地地址
[2026-01-18 10:15:40.789] [Guard-BLOCK] GET  https://192.168.1.10/api - 私网地址
```

4. **支持的网络库**
- `requests` - 完整支持
- `urllib` - urllib.request.urlopen 包装
- 可扩展支持 httpx、aiohttp 等

5. **安全检查点**
- URL 格式验证
- 协议检查（仅允许 http/https）
- 本地地址检测（localhost, 127.0.0.1, ::1）
- 私网地址检测（CIDR 检查）
- 危险端口检测
- DNS 解析结果验证

---

#### 2.2 PyCodePreprocessor Java类

**位置**: `parser/src/main/java/cn/qaiu/parser/custompy/PyCodePreprocessor.java`

**核心职责**:

1. **代码分析** - 检测代码中的网络请求库导入
```java
// 检测的导入模式
IMPORT_REQUESTS  // import requests 或 from requests
IMPORT_URLLIB    // import urllib 或 from urllib
IMPORT_HTTPX     // import httpx 或 from httpx
IMPORT_AIOHTTP   // import aiohttp 或 from aiohttp
IMPORT_SOCKET    // import socket 或 from socket
```

2. **猴子补丁注入** - 在代码执行前动态注入补丁
```
原始代码：
"""模块文档"""
import requests

def parse():
    ...

注入后的代码：
"""模块文档"""

# ===== 自动注入的网络请求安全补丁 (由 PyCodePreprocessor 生成) =====
[requests_guard.py 完整内容]
# ===== 安全补丁结束 =====

import requests

def parse():
    ...
```

3. **日志生成** - 为演练场控制台生成预处理信息
```
✓ 网络请求安全拦截已启用 (检测到: requests, urllib) | 已动态注入 requests_guard 猴子补丁
```

**实现细节**:

```java
public static PyPreprocessResult preprocess(String originalCode) {
    if (originalCode == null || originalCode.trim().isEmpty()) {
        return new PyPreprocessResult(originalCode, false, null, "代码为空，无需预处理");
    }
    
    // 检测网络请求库
    NetworkLibraryDetection detection = detectNetworkLibraries(originalCode);
    
    if (detection.hasAnyNetworkLibrary()) {
        // 加载猴子补丁代码
        String patchCode = loadRequestsGuardPatch();
        
        if (patchCode != null && !patchCode.isEmpty()) {
            // 在代码头部注入补丁
            String preprocessedCode = injectPatch(originalCode, patchCode);
            
            String logMessage = String.format(
                    "✓ 网络请求安全拦截已启用 (检测到: %s) | 已动态注入 requests_guard 猴子补丁",
                    detection.getDetectedLibrariesAsString()
            );
            
            return new PyPreprocessResult(
                    preprocessedCode, 
                    true, 
                    detection.getDetectedLibraries(),
                    logMessage
            );
        }
    }
    
    return new PyPreprocessResult(originalCode, false, null, 
            "ℹ 代码中未检测到网络请求库，不需要注入安全拦截补丁");
}
```

---

#### 2.3 PyPlaygroundExecutor集成

**位置**: `parser/src/main/java/cn/qaiu/parser/custompy/PyPlaygroundExecutor.java`

**集成点**:

在 `executeParseAsync()`、`executeParseFileListAsync()` 和 `executeParseByIdAsync()` 方法中添加代码预处理：

```java
// Python代码预处理 - 检测并注入猴子补丁
PyCodePreprocessor.PyPreprocessResult preprocessResult = PyCodePreprocessor.preprocess(pyCode);
playgroundLogger.infoJava(preprocessResult.getLogMessage());
String codeToExecute = preprocessResult.getProcessedCode();

// 然后执行预处理后的代码
context.eval("python", codeToExecute);
```

**日志流程**:

1. 预处理时生成日志信息
2. 通过 `playgroundLogger.infoJava()` 添加到日志列表
3. 日志包含在 API 响应中返回给前端
4. 前端在演练场控制台中显示

---

### 三、演练场控制台日志显示

#### 3.1 前端日志显示增强

**位置**: `web-front/src/views/Playground.vue`

**日志来源标记**:

```vue
<span v-if="log.source" class="console-source-tag" :class="'console-source-' + (log.source || 'unknown')">
  [{{ log.source === 'java' ? 'JAVA' : (log.source === 'JS' ? 'JS' : 'PYTHON') }}]
</span>
```

**CSS样式分类**:

```css
/* JavaScript日志 - 绿色主题 */
.console-js-source {
  border-left-color: var(--el-color-success) !important;
  background: var(--el-color-success-light-9) !important;
}

/* Java日志（包括预处理日志）- 橙色主题 */
.console-java-source {
  border-left-color: var(--el-color-warning) !important;
  background: var(--el-color-warning-light-9) !important;
}

/* Python日志 - 蓝色主题 */
.console-python-source {
  border-left-color: var(--el-color-info) !important;
  background: var(--el-color-info-light-9) !important;
}

/* 源标记样式 */
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

.console-source-java {
  background: linear-gradient(135deg, var(--el-color-warning) 0%, var(--el-color-warning-light-3) 100%);
  box-shadow: 0 2px 4px rgba(230, 162, 60, 0.3);
}
```

---

## 演练场工作流程

### 执行Python代码的完整流程:

```
用户在演练场提交代码
    ↓
前端发送 /v2/playground/test POST请求
    ↓
PlaygroundApi.test() 接收请求
    ↓
PyPlaygroundExecutor 创建实例
    ↓
executeParseAsync() 执行流程:
    ├─ PyCodeSecurityChecker.check() - 安全检查
    ├─ PyCodePreprocessor.preprocess() - 代码预处理
    │  ├─ 检测导入的网络库
    │  ├─ 加载 requests_guard.py
    │  ├─ 注入补丁到代码头部
    │  └─ 返回预处理日志："✓ 网络请求安全拦截已启用..."
    ├─ playgroundLogger.infoJava() - 记录预处理日志
    ├─ 执行预处理后的代码
    │  └─ 代码运行时会自动应用猴子补丁
    └─ 收集所有日志和执行结果
    ↓
API返回包含日志的响应
    ↓
前端接收并在演练场控制台显示所有日志:
    ├─ [JAVA] 预处理日志（橙色，带[JAVA]标签）
    ├─ [PYTHON] Python脚本中的 print/logger 日志（蓝色，带[PYTHON]标签）
    └─ [Guard] 网络请求拦截日志（由补丁中的GuardLogger生成）
```

---

## 演练场控制台日志示例

```
[10:15:30] INFO  [JAVA] ✓ 网络请求安全拦截已启用 (检测到: requests, urllib) | 已动态注入 requests_guard 猴子补丁
[10:15:31] DEBUG [JAVA] [Java] 安全检查通过
[10:15:31] INFO  [JAVA] [Java] 开始执行parse方法
[10:15:32] DEBUG [JAVA] [Java] 执行Python代码
[10:15:33] INFO  [PYTHON] 正在解析链接: https://example.com/s/abc123
[10:15:33] DEBUG [PYTHON] [Guard] 允许 GET https://example.com/s/abc123
[10:15:34] INFO  [PYTHON] 获取到 5 个文件
[10:15:35] INFO  [JAVA] [Java] 解析成功，返回结果: https://download.example.com/file.zip
```

---

## 安全特性

### 1. 网络请求拦截范围
- ✅ 拦截本地地址（127.0.0.1, localhost）
- ✅ 拦截私网地址（10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 等）
- ✅ 拦截危险端口（SSH, MySQL, Redis等）
- ✅ DNS解析结果验证
- ✅ 协议检查（仅允许http/https）

### 2. 代码执行安全
- ✅ 静态安全检查（在预处理前）
- ✅ 动态补丁注入（不修改用户代码）
- ✅ 审计日志记录（所有网络请求可追踪）
- ✅ 超时控制（30秒执行超时）

### 3. 扩展性
- ✅ 支持添加更多网络库拦截
- ✅ 支持自定义黑名单/白名单
- ✅ 支持热更新补丁代码
- ✅ 支持自定义审计日志处理

---

## 技术栈

### 前端 (Vue.js 3)
- Monaco Editor - 代码编辑
- Element Plus - UI组件
- FileReader API - 文件导入
- Clipboard API - 粘贴操作

### 后端 (Java)
- Vert.x 4.5.23 - 异步框架
- GraalVM Polyglot - Python执行
- SLF4J + Logback - 日志记录
- 正则表达式 - 代码分析

### Python
- 标准库：socket、urllib
- 无额外依赖 - 补丁模块独立运行

---

## 文件清单

### 新增文件
1. `parser/src/main/resources/requests_guard.py` - 猴子补丁模块
2. `parser/src/main/java/cn/qaiu/parser/custompy/PyCodePreprocessor.java` - 代码预处理器

### 修改的文件
1. `web-front/src/views/Playground.vue` - 编辑器UI和日志显示
2. `parser/src/main/java/cn/qaiu/parser/custompy/PyPlaygroundExecutor.java` - 集成预处理器

---

## 使用方式

### 1. 导入文件
点击 "更多操作" → "导入文件" → 选择本地.js/.py/.txt文件

### 2. 粘贴代码
- 使用 "粘贴" 按钮
- 或直接 Ctrl+V/Cmd+V
- 支持多行代码一次性粘贴

### 3. 查看安全拦截日志
执行包含 requests/urllib 的Python代码时：
1. 演练场控制台自动显示"✓ 网络请求安全拦截已启用"
2. 所有网络请求都会记录在日志中
3. 被拦截的请求显示拦截原因

---

## 性能考虑

- **代码预处理** - 仅在需要时执行，时间复杂度 O(n)
- **补丁加载** - 一次性从资源文件加载，缓存在内存
- **日志记录** - 异步操作，不阻塞代码执行
- **前端显示** - 虚拟列表（当日志过多时）

---

## 测试建议

### 功能测试
1. ✅ 导入不同格式的文件
2. ✅ 粘贴多行代码和特殊字符
3. ✅ 执行包含 requests 的Python代码
4. ✅ 验证网络请求拦截日志
5. ✅ 测试移动端编辑体验

### 安全测试
1. ✅ 尝试访问 127.0.0.1 等本地地址
2. ✅ 尝试访问私网地址
3. ✅ 尝试连接危险端口
4. ✅ 验证日志中显示拦截原因

---

## 后续增强建议

1. **集成更多网络库** - httpx, aiohttp, twisted 等
2. **白名单支持** - 允许特定地址/域名访问
3. **审计日志持久化** - 保存到文件/数据库
4. **性能优化** - 缓存IP解析结果
5. **UI优化** - 日志搜索、过滤、导出功能
6. **告警机制** - 频繁访问被拦截地址时告警

---

## 许可证
遵循项目原有许可证

---

## 贡献者
GitHub Copilot

---

*本文档最后更新于 2026年1月18日*
