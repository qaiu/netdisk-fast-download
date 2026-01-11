# Python Playground pylsp WebSocket 集成指南

## 概述

本文档说明了如何将 jedi 的 pylsp (python-lsp-server) 通过 WebSocket 集成到 Python Playground 中，实现实时代码检查、自动完成和悬停提示等功能。

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                     前端 (Vue + Monaco)                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  PylspClient.js                                         ││
│  │  - 通过 WebSocket 发送 LSP JSON-RPC 消息                 ││
│  │  - 接收诊断信息并转换为 Monaco markers                   ││
│  └─────────────────────────────────────────────────────────┘│
└──────────────────────────┬──────────────────────────────────┘
                           │ WebSocket (SockJS)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│               后端 (Vert.x + SockJS)                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  PylspWebSocketHandler.java                             ││
│  │  - @SockRouteMapper("/pylsp/")                          ││
│  │  - 管理 pylsp 子进程                                     ││
│  │  - 转发 LSP 消息                                         ││
│  └─────────────────────────────────────────────────────────┘│
└──────────────────────────┬──────────────────────────────────┘
                           │ stdio (LSP协议)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                pylsp (python-lsp-server)                    │
│  - jedi: 代码补全、定义跳转                                  │
│  - pyflakes: 语法错误检查                                    │
│  - pycodestyle: PEP8 风格检查                               │
│  - mccabe: 复杂度检查                                       │
└─────────────────────────────────────────────────────────────┘
```

## 文件清单

### 后端 (Java)

1. **PylspWebSocketHandler.java**
   - 路径: `web-service/src/main/java/cn/qaiu/lz/web/controller/PylspWebSocketHandler.java`
   - 功能: WebSocket 端点，桥接前端与 pylsp 子进程
   - 端点: `/ws/pylsp/*`

### 前端 (JavaScript/Vue)

1. **pylspClient.js**
   - 路径: `web-front/src/utils/pylspClient.js`
   - 功能: LSP WebSocket 客户端，封装 LSP 协议

### 测试

1. **RequestsIntegrationTest.java**
   - 路径: `web-service/src/test/java/cn/qaiu/lz/web/playground/RequestsIntegrationTest.java`
   - 功能: requests 库集成测试

2. **test_playground_api.py**
   - 路径: `web-service/src/test/python/test_playground_api.py`
   - 功能: API 接口的 pytest 测试脚本

## 使用方法

### 1. 安装 pylsp

```bash
pip install python-lsp-server[all]
```

或者只安装核心功能：

```bash
pip install python-lsp-server jedi
```

### 2. 前端集成示例

```javascript
import PylspClient from '@/utils/pylspClient';

// 创建客户端
const pylsp = new PylspClient({
  onDiagnostics: (uri, markers) => {
    // 设置 Monaco Editor markers
    monaco.editor.setModelMarkers(model, 'pylsp', markers);
  },
  onConnected: () => {
    console.log('pylsp 已连接');
  },
  onError: (error) => {
    console.error('pylsp 错误:', error);
  }
});

// 连接
await pylsp.connect();

// 打开文档
pylsp.openDocument(pythonCode);

// 更新文档（当代码改变时）
pylsp.updateDocument(newCode);

// 获取补全
const completions = await pylsp.getCompletions(line, column);

// 获取悬停信息
const hover = await pylsp.getHover(line, column);

// 断开连接
pylsp.disconnect();
```

### 3. 与 Monaco Editor 集成

```javascript
// 监听代码变化
editor.onDidChangeModelContent((e) => {
  const content = editor.getValue();
  pylsp.updateDocument(content);
});

// 注册补全提供者
monaco.languages.registerCompletionItemProvider('python', {
  provideCompletionItems: async (model, position) => {
    const items = await pylsp.getCompletions(
      position.lineNumber - 1,
      position.column - 1
    );
    return { suggestions: items.map(convertToMonacoItem) };
  }
});
```

## 已知限制

### GraalPy requests 库限制

由于 GraalPy 的 `unicodedata/LLVM` 限制，`requests` 库在后续创建的 Context 中无法正常导入（会抛出 `PolyglotException: null`）。

**错误链**：
```
requests → encodings.idna → stringprep → from unicodedata import ucd_3_2_0
```

**解决方案**：
1. 在代码顶层导入 requests（不要在函数内部导入）
2. 使用标准库的 `urllib.request` 作为替代
3. 首次执行时预热 requests 导入

### 测试注意事项

1. PyPlaygroundFullTest 中的测试2和测试5被标记为跳过（已知限制）
2. 测试13（前端模板代码）使用不依赖 requests 的版本
3. requests 功能在实际运行时通过首个 Context 可以正常使用

## 测试命令

### 运行 Java 单元测试

```bash
# PyPlaygroundFullTest (13 个测试)
cd parser && mvn exec:java \
  -Dexec.mainClass="cn.qaiu.parser.custompy.PyPlaygroundFullTest" \
  -Dexec.classpathScope=test -q

# RequestsIntegrationTest
cd web-service && mvn exec:java \
  -Dexec.mainClass="cn.qaiu.lz.web.playground.RequestsIntegrationTest" \
  -Dexec.classpathScope=test -q
```

### 运行 Python API 测试

```bash
# 需要后端服务运行
cd web-service/src/test/python
pip install pytest requests
pytest test_playground_api.py -v
```

## 配置

### 后端配置

`PylspWebSocketHandler.java` 中可以配置：
- pylsp 启动命令
- 心跳间隔
- 进程超时

### 前端配置

`pylspClient.js` 中可以配置：
- WebSocket URL
- 重连次数
- 重连延迟
- 请求超时

## 安全考虑

1. pylsp 进程在沙箱环境中运行
2. 每个 WebSocket 连接对应一个独立的 pylsp 进程
3. 连接关闭时自动清理进程
4. Playground 访问需要认证（如果配置了密码）

## 未来改进

1. 支持多文件项目分析
2. 添加 pyright 类型检查
3. 支持代码格式化（black/autopep8）
4. 添加重构功能
5. 支持虚拟环境选择
