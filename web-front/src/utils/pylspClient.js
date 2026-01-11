/**
 * Python LSP (pylsp/jedi) WebSocket 客户端
 * 
 * 通过 WebSocket 连接到后端 pylsp 桥接服务，
 * 提供实时代码检查、自动完成、悬停提示等功能。
 * 
 * @author QAIU
 */

import SockJS from 'sockjs-client';

// LSP 消息类型
const LSP_METHODS = {
  INITIALIZE: 'initialize',
  INITIALIZED: 'initialized',
  TEXT_DOCUMENT_DID_OPEN: 'textDocument/didOpen',
  TEXT_DOCUMENT_DID_CHANGE: 'textDocument/didChange',
  TEXT_DOCUMENT_DID_CLOSE: 'textDocument/didClose',
  TEXT_DOCUMENT_COMPLETION: 'textDocument/completion',
  TEXT_DOCUMENT_HOVER: 'textDocument/hover',
  TEXT_DOCUMENT_DIAGNOSTICS: 'textDocument/publishDiagnostics',
  SHUTDOWN: 'shutdown',
  EXIT: 'exit'
};

// 诊断严重程度
const DiagnosticSeverity = {
  Error: 1,
  Warning: 2,
  Information: 3,
  Hint: 4
};

/**
 * pylsp WebSocket 客户端类
 */
class PylspClient {
  constructor(options = {}) {
    this.wsUrl = options.wsUrl || this._getDefaultWsUrl();
    this.ws = null;
    this.requestId = 1;
    this.pendingRequests = new Map();
    this.documentUri = 'file:///playground.py';
    this.documentVersion = 0;
    
    // 回调函数
    this.onDiagnostics = options.onDiagnostics || (() => {});
    this.onConnected = options.onConnected || (() => {});
    this.onDisconnected = options.onDisconnected || (() => {});
    this.onError = options.onError || (() => {});
    
    // 状态
    this.connected = false;
    this.initialized = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 3;
    this.reconnectDelay = 2000;
  }
  
  /**
   * 获取默认 WebSocket URL
   * SockJS 客户端需要使用 HTTP/HTTPS URL，而不是 WS/WSS
   */
  _getDefaultWsUrl() {
    const protocol = window.location.protocol; // http: 或 https:
    const host = window.location.host;
    return `${protocol}//${host}/v2/ws/pylsp`;
  }
  
  /**
   * 连接到 pylsp 服务
   */
  async connect() {
    if (this.connected) {
      console.log('[PylspClient] 已经连接');
      return true;
    }
    
    return new Promise((resolve, reject) => {
      try {
        console.log('[PylspClient] 正在连接:', this.wsUrl);
        
        // 使用 SockJS 连接（支持 WebSocket 和 fallback）
        this.ws = new SockJS(this.wsUrl);
        
        this.ws.onopen = () => {
          console.log('[PylspClient] WebSocket 连接成功');
          this.connected = true;
          this.reconnectAttempts = 0;
          this._initialize().then(() => {
            this.onConnected();
            resolve(true);
          }).catch(err => {
            console.error('[PylspClient] 初始化失败:', err);
            reject(err);
          });
        };
        
        this.ws.onmessage = (event) => {
          this._handleMessage(event.data);
        };
        
        this.ws.onerror = (error) => {
          console.error('[PylspClient] WebSocket 错误:', error);
          this.onError(error);
        };
        
        this.ws.onclose = () => {
          console.log('[PylspClient] WebSocket 连接关闭');
          this.connected = false;
          this.initialized = false;
          this.onDisconnected();
          
          // 尝试重连
          if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`[PylspClient] 尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => this.connect(), this.reconnectDelay);
          }
        };
        
        // 设置超时
        setTimeout(() => {
          if (!this.connected) {
            reject(new Error('连接超时'));
          }
        }, 10000);
        
      } catch (error) {
        console.error('[PylspClient] 连接失败:', error);
        reject(error);
      }
    });
  }
  
  /**
   * 断开连接
   */
  disconnect() {
    if (this.ws) {
      this._sendRequest(LSP_METHODS.SHUTDOWN).then(() => {
        this._sendNotification(LSP_METHODS.EXIT);
        this.ws.close();
      }).catch(() => {
        this.ws.close();
      });
    }
    this.connected = false;
    this.initialized = false;
  }
  
  /**
   * 初始化 LSP
   */
  async _initialize() {
    const params = {
      processId: null,
      rootUri: null,
      capabilities: {
        textDocument: {
          synchronization: {
            dynamicRegistration: false,
            willSave: false,
            willSaveWaitUntil: false,
            didSave: true
          },
          completion: {
            dynamicRegistration: false,
            completionItem: {
              snippetSupport: true,
              commitCharactersSupport: true,
              documentationFormat: ['markdown', 'plaintext'],
              deprecatedSupport: true
            }
          },
          hover: {
            dynamicRegistration: false,
            contentFormat: ['markdown', 'plaintext']
          },
          publishDiagnostics: {
            relatedInformation: true
          }
        }
      }
    };
    
    await this._sendRequest(LSP_METHODS.INITIALIZE, params);
    this._sendNotification(LSP_METHODS.INITIALIZED, {});
    this.initialized = true;
    console.log('[PylspClient] LSP 初始化完成');
  }
  
  /**
   * 打开文档
   */
  openDocument(content, uri = this.documentUri) {
    if (!this.initialized) {
      console.warn('[PylspClient] LSP 未初始化');
      return;
    }
    
    this.documentUri = uri;
    this.documentVersion = 1;
    
    this._sendNotification(LSP_METHODS.TEXT_DOCUMENT_DID_OPEN, {
      textDocument: {
        uri: uri,
        languageId: 'python',
        version: this.documentVersion,
        text: content
      }
    });
  }
  
  /**
   * 更新文档内容
   */
  updateDocument(content, uri = this.documentUri) {
    if (!this.initialized) {
      return;
    }
    
    this.documentVersion++;
    
    this._sendNotification(LSP_METHODS.TEXT_DOCUMENT_DID_CHANGE, {
      textDocument: {
        uri: uri,
        version: this.documentVersion
      },
      contentChanges: [{ text: content }]
    });
  }
  
  /**
   * 关闭文档
   */
  closeDocument(uri = this.documentUri) {
    if (!this.initialized) {
      return;
    }
    
    this._sendNotification(LSP_METHODS.TEXT_DOCUMENT_DID_CLOSE, {
      textDocument: { uri: uri }
    });
  }
  
  /**
   * 获取补全建议
   */
  async getCompletions(line, character, uri = this.documentUri) {
    if (!this.initialized) {
      return [];
    }
    
    try {
      const result = await this._sendRequest(LSP_METHODS.TEXT_DOCUMENT_COMPLETION, {
        textDocument: { uri: uri },
        position: { line, character }
      });
      
      return result?.items || result || [];
    } catch (error) {
      console.error('[PylspClient] 获取补全失败:', error);
      return [];
    }
  }
  
  /**
   * 获取悬停信息
   */
  async getHover(line, character, uri = this.documentUri) {
    if (!this.initialized) {
      return null;
    }
    
    try {
      return await this._sendRequest(LSP_METHODS.TEXT_DOCUMENT_HOVER, {
        textDocument: { uri: uri },
        position: { line, character }
      });
    } catch (error) {
      console.error('[PylspClient] 获取悬停信息失败:', error);
      return null;
    }
  }
  
  /**
   * 发送 LSP 请求
   */
  _sendRequest(method, params = {}) {
    return new Promise((resolve, reject) => {
      // SockJS readyState: 0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED
      if (!this.ws || this.ws.readyState !== 1) {
        reject(new Error('WebSocket 未连接'));
        return;
      }
      
      const id = this.requestId++;
      const message = {
        jsonrpc: '2.0',
        id: id,
        method: method,
        params: params
      };
      
      this.pendingRequests.set(id, { resolve, reject });
      this.ws.send(JSON.stringify(message));
      
      // 设置超时
      setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          reject(new Error(`请求超时: ${method}`));
        }
      }, 30000);
    });
  }
  
  /**
   * 发送 LSP 通知（无需响应）
   */
  _sendNotification(method, params = {}) {
    // SockJS readyState: 0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED
    if (!this.ws || this.ws.readyState !== 1) {
      return;
    }
    
    const message = {
      jsonrpc: '2.0',
      method: method,
      params: params
    };
    
    this.ws.send(JSON.stringify(message));
  }
  
  /**
   * 处理接收到的消息
   */
  _handleMessage(data) {
    try {
      const message = JSON.parse(data);
      
      // 响应消息
      if (message.id !== undefined) {
        const pending = this.pendingRequests.get(message.id);
        if (pending) {
          this.pendingRequests.delete(message.id);
          if (message.error) {
            pending.reject(new Error(message.error.message || '未知错误'));
          } else {
            pending.resolve(message.result);
          }
        }
        return;
      }
      
      // 通知消息
      if (message.method === LSP_METHODS.TEXT_DOCUMENT_DIAGNOSTICS) {
        this._handleDiagnostics(message.params);
      }
      
    } catch (error) {
      console.error('[PylspClient] 解析消息失败:', error);
    }
  }
  
  /**
   * 处理诊断信息
   */
  _handleDiagnostics(params) {
    const { uri, diagnostics } = params;
    
    // 转换为 Monaco Editor 格式
    const monacoMarkers = diagnostics.map(d => ({
      severity: this._convertSeverity(d.severity),
      startLineNumber: d.range.start.line + 1,
      startColumn: d.range.start.character + 1,
      endLineNumber: d.range.end.line + 1,
      endColumn: d.range.end.character + 1,
      message: d.message,
      source: d.source || 'pylsp'
    }));
    
    this.onDiagnostics(uri, monacoMarkers);
  }
  
  /**
   * 转换诊断严重程度到 Monaco 格式
   */
  _convertSeverity(lspSeverity) {
    // Monaco MarkerSeverity: Error = 8, Warning = 4, Info = 2, Hint = 1
    switch (lspSeverity) {
      case DiagnosticSeverity.Error: return 8;
      case DiagnosticSeverity.Warning: return 4;
      case DiagnosticSeverity.Information: return 2;
      case DiagnosticSeverity.Hint: return 1;
      default: return 4;
    }
  }
}

// 导出
export {
  PylspClient,
  LSP_METHODS,
  DiagnosticSeverity
};

export default PylspClient;
