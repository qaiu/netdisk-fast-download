/**
 * Monaco Editor 代码补全配置工具
 * 基于 types.js 提供完整的代码补全支持
 * 支持 JavaScript 和 Python 两种语言
 */

/**
 * 配置Monaco Editor的类型定义和代码补全
 * @param {monaco} monaco - Monaco Editor实例
 */
export async function configureMonacoTypes(monaco) {
  if (!monaco) {
    console.warn('Monaco Editor未初始化');
    return;
  }

  // 注册JavaScript语言特性
  monaco.languages.setLanguageConfiguration('javascript', {
    comments: {
      lineComment: '//',
      blockComment: ['/*', '*/']
    },
    brackets: [
      ['{', '}'],
      ['[', ']'],
      ['(', ')']
    ],
    autoClosingPairs: [
      { open: '{', close: '}' },
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" }
    ],
    surroundingPairs: [
      { open: '{', close: '}' },
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" }
    ]
  });

  // 注册类型定义
  registerTypeDefinitions(monaco);

  // 注册代码补全提供者
  registerCompletionProvider(monaco);
  
  // 注册Python语言补全提供者
  registerPythonCompletionProvider(monaco);
}

/**
 * 注册类型定义
 */
function registerTypeDefinitions(monaco) {
  // ShareLinkInfo类型定义
  const shareLinkInfoType = `
    interface ShareLinkInfo {
      getShareUrl(): string;
      getShareKey(): string;
      getSharePassword(): string;
      getType(): string;
      getPanName(): string;
      getOtherParam(key: string): any;
      hasOtherParam(key: string): boolean;
      getOtherParamAsString(key: string): string | null;
      getOtherParamAsInteger(key: string): number | null;
      getOtherParamAsBoolean(key: string): boolean | null;
    }
  `;

  // JsHttpClient类型定义
  const httpClientType = `
    interface JsHttpClient {
      get(url: string): JsHttpResponse;
      getWithRedirect(url: string): JsHttpResponse;
      getNoRedirect(url: string): JsHttpResponse;
      post(url: string, data?: any): JsHttpResponse;
      put(url: string, data?: any): JsHttpResponse;
      delete(url: string): JsHttpResponse;
      patch(url: string, data?: any): JsHttpResponse;
      putHeader(name: string, value: string): JsHttpClient;
      putHeaders(headers: Record<string, string>): JsHttpClient;
      removeHeader(name: string): JsHttpClient;
      clearHeaders(): JsHttpClient;
      getHeaders(): Record<string, string>;
      setTimeout(seconds: number): JsHttpClient;
      sendForm(data: Record<string, any>): JsHttpResponse;
      sendMultipartForm(url: string, data: Record<string, any>): JsHttpResponse;
      sendJson(data: any): JsHttpResponse;
      urlEncode(str: string): string;
      urlDecode(str: string): string;
    }
  `;

  // JsHttpResponse类型定义
  const httpResponseType = `
    interface JsHttpResponse {
      body(): string;
      json(): any;
      statusCode(): number;
      header(name: string): string | null;
      headers(): Record<string, string>;
      isSuccess(): boolean;
      bodyBytes(): number[];
      bodySize(): number;
    }
  `;

  // JsLogger类型定义
  const loggerType = `
    interface JsLogger {
      debug(message: string, ...args: any[]): void;
      info(message: string, ...args: any[]): void;
      warn(message: string, ...args: any[]): void;
      error(message: string, ...args: any[]): void;
      isDebugEnabled(): boolean;
      isInfoEnabled(): boolean;
      isWarnEnabled(): boolean;
      isErrorEnabled(): boolean;
    }
  `;

  // FileInfo类型定义
  const fileInfoType = `
    interface FileInfo {
      fileName: string;
      fileId: string;
      fileType: 'file' | 'folder';
      size: number;
      sizeStr: string;
      createTime: string;
      updateTime?: string;
      createBy?: string;
      downloadCount?: number;
      fileIcon?: string;
      panType?: string;
      parserUrl?: string;
      previewUrl?: string;
    }
  `;

  // 合并所有类型定义
  const allTypes = `
    ${shareLinkInfoType}
    ${httpClientType}
    ${httpResponseType}
    ${loggerType}
    ${fileInfoType}

    // 全局变量声明
    declare var shareLinkInfo: ShareLinkInfo;
    declare var http: JsHttpClient;
    declare var logger: JsLogger;
  `;

  // 注册类型定义到Monaco
  monaco.languages.typescript.javascriptDefaults.addExtraLib(
    allTypes,
    'file:///types.d.ts'
  );
}

/**
 * 注册代码补全提供者
 */
function registerCompletionProvider(monaco) {
  monaco.languages.registerCompletionItemProvider('javascript', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      };

      const suggestions = [
        // ShareLinkInfo方法
        {
          label: 'shareLinkInfo.getShareUrl()',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getShareUrl()',
          documentation: '获取分享URL',
          range
        },
        {
          label: 'shareLinkInfo.getShareKey()',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getShareKey()',
          documentation: '获取分享Key',
          range
        },
        {
          label: 'shareLinkInfo.getSharePassword()',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getSharePassword()',
          documentation: '获取分享密码',
          range
        },
        {
          label: 'shareLinkInfo.getType()',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getType()',
          documentation: '获取网盘类型',
          range
        },
        {
          label: 'shareLinkInfo.getPanName()',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getPanName()',
          documentation: '获取网盘名称',
          range
        },
        {
          label: 'shareLinkInfo.getOtherParam(key)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'shareLinkInfo.getOtherParam(${1:key})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '获取其他参数',
          range
        },
        // JsHttpClient方法
        {
          label: 'http.get(url)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'http.get(${1:url})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '发起GET请求',
          range
        },
        {
          label: 'http.post(url, data)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'http.post(${1:url}, ${2:data})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '发起POST请求',
          range
        },
        {
          label: 'http.putHeader(name, value)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'http.putHeader(${1:name}, ${2:value})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '设置请求头',
          range
        },
        {
          label: 'http.sendForm(data)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'http.sendForm(${1:data})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '发送表单数据',
          range
        },
        {
          label: 'http.sendJson(data)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'http.sendJson(${1:data})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '发送JSON数据',
          range
        },
        // JsLogger方法
        {
          label: 'logger.info(message)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'logger.info(${1:message})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '记录信息日志',
          range
        },
        {
          label: 'logger.debug(message)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'logger.debug(${1:message})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '记录调试日志',
          range
        },
        {
          label: 'logger.warn(message)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'logger.warn(${1:message})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '记录警告日志',
          range
        },
        {
          label: 'logger.error(message)',
          kind: monaco.languages.CompletionItemKind.Method,
          insertText: 'logger.error(${1:message})',
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: '记录错误日志',
          range
        }
      ];

      return { suggestions };
    }
  });
}

/**
 * 注册Python语言补全提供者
 * 提供 requests 库、内置对象和常用模块的代码补全
 */
function registerPythonCompletionProvider(monaco) {
  monaco.languages.registerCompletionItemProvider('python', {
    triggerCharacters: ['.', '(', '"', "'"],
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      };

      // 获取当前行内容以判断上下文
      const lineContent = model.getLineContent(position.lineNumber);
      const textBeforeCursor = lineContent.substring(0, position.column - 1);
      
      const suggestions = [];
      
      // ===== requests 库补全 =====
      if (textBeforeCursor.endsWith('requests.') || textBeforeCursor.match(/requests\s*\.\s*$/)) {
        suggestions.push(
          {
            label: 'get',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get(${1:url}, params=${2:None}, headers=${3:None})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 GET 请求\n\n参数:\n- url: 请求URL\n- params: URL参数字典\n- headers: 请求头字典\n\n返回: Response 对象',
            range
          },
          {
            label: 'post',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'post(${1:url}, data=${2:None}, json=${3:None}, headers=${4:None})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 POST 请求\n\n参数:\n- url: 请求URL\n- data: 表单数据\n- json: JSON数据\n- headers: 请求头字典\n\n返回: Response 对象',
            range
          },
          {
            label: 'put',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'put(${1:url}, data=${2:None})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 PUT 请求',
            range
          },
          {
            label: 'delete',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'delete(${1:url})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 DELETE 请求',
            range
          },
          {
            label: 'patch',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'patch(${1:url}, data=${2:None})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 PATCH 请求',
            range
          },
          {
            label: 'head',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'head(${1:url})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 HEAD 请求',
            range
          },
          {
            label: 'Session',
            kind: monaco.languages.CompletionItemKind.Class,
            insertText: 'Session()',
            documentation: '创建一个会话对象，可以跨请求保持 cookies 和 headers',
            range
          },
          {
            label: 'url_encode',
            kind: monaco.languages.CompletionItemKind.Function,
            insertText: 'url_encode(${1:text})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'URL 编码',
            range
          },
          {
            label: 'url_decode',
            kind: monaco.languages.CompletionItemKind.Function,
            insertText: 'url_decode(${1:text})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'URL 解码',
            range
          }
        );
      }
      // Response 对象补全
      else if (textBeforeCursor.match(/\.\s*$/) && (
        textBeforeCursor.includes('response') || 
        textBeforeCursor.includes('resp') || 
        textBeforeCursor.includes('res') ||
        textBeforeCursor.match(/requests\.(get|post|put|delete|patch|head)\([^)]*\)\s*\./)
      )) {
        suggestions.push(
          {
            label: 'text',
            kind: monaco.languages.CompletionItemKind.Property,
            insertText: 'text',
            documentation: '响应的文本内容',
            range
          },
          {
            label: 'content',
            kind: monaco.languages.CompletionItemKind.Property,
            insertText: 'content',
            documentation: '响应的二进制内容',
            range
          },
          {
            label: 'json',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'json()',
            documentation: '解析响应为 JSON 对象',
            range
          },
          {
            label: 'status_code',
            kind: monaco.languages.CompletionItemKind.Property,
            insertText: 'status_code',
            documentation: 'HTTP 状态码',
            range
          },
          {
            label: 'ok',
            kind: monaco.languages.CompletionItemKind.Property,
            insertText: 'ok',
            documentation: '请求是否成功 (status_code < 400)',
            range
          },
          {
            label: 'headers',
            kind: monaco.languages.CompletionItemKind.Property,
            insertText: 'headers',
            documentation: '响应头字典',
            range
          },
          {
            label: 'raise_for_status',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'raise_for_status()',
            documentation: '如果响应状态码表示错误，则抛出异常',
            range
          }
        );
      }
      // share_link_info 对象补全
      else if (textBeforeCursor.endsWith('share_link_info.')) {
        suggestions.push(
          {
            label: 'get_share_url',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_share_url()',
            documentation: '获取分享URL',
            range
          },
          {
            label: 'get_share_key',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_share_key()',
            documentation: '获取分享Key',
            range
          },
          {
            label: 'get_share_password',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_share_password()',
            documentation: '获取分享密码',
            range
          },
          {
            label: 'get_type',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_type()',
            documentation: '获取网盘类型',
            range
          },
          {
            label: 'get_pan_name',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_pan_name()',
            documentation: '获取网盘名称',
            range
          },
          {
            label: 'get_other_param',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_other_param(${1:key})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '获取其他参数',
            range
          }
        );
      }
      // http 对象补全（Python 风格下划线命名）
      else if (textBeforeCursor.endsWith('http.')) {
        suggestions.push(
          {
            label: 'get',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get(${1:url})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 GET 请求',
            range
          },
          {
            label: 'get_with_redirect',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_with_redirect(${1:url})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 GET 请求并跟随重定向',
            range
          },
          {
            label: 'get_no_redirect',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'get_no_redirect(${1:url})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 GET 请求但不跟随重定向',
            range
          },
          {
            label: 'post',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'post(${1:url}, ${2:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 POST 请求',
            range
          },
          {
            label: 'post_json',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'post_json(${1:url}, ${2:json_data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '发起 POST 请求（JSON 数据）',
            range
          },
          {
            label: 'put_header',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'put_header(${1:name}, ${2:value})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '设置请求头',
            range
          },
          {
            label: 'put_headers',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'put_headers(${1:headers_dict})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '批量设置请求头',
            range
          },
          {
            label: 'set_timeout',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'set_timeout(${1:seconds})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '设置请求超时时间（秒）',
            range
          },
          {
            label: 'url_encode',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'url_encode(${1:text})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'URL 编码',
            range
          },
          {
            label: 'url_decode',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'url_decode(${1:text})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'URL 解码',
            range
          }
        );
      }
      // logger 对象补全
      else if (textBeforeCursor.endsWith('logger.')) {
        suggestions.push(
          {
            label: 'info',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'info(${1:message})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '记录信息日志',
            range
          },
          {
            label: 'debug',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'debug(${1:message})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '记录调试日志',
            range
          },
          {
            label: 'warn',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'warn(${1:message})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '记录警告日志',
            range
          },
          {
            label: 'error',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'error(${1:message})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '记录错误日志',
            range
          }
        );
      }
      // crypto 加密工具补全
      else if (textBeforeCursor.endsWith('crypto.')) {
        suggestions.push(
          {
            label: 'md5',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'md5(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'MD5 加密（返回32位小写）',
            range
          },
          {
            label: 'md5_16',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'md5_16(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'MD5 加密（返回16位小写）',
            range
          },
          {
            label: 'sha1',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'sha1(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'SHA-1 加密',
            range
          },
          {
            label: 'sha256',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'sha256(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'SHA-256 加密',
            range
          },
          {
            label: 'base64_encode',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'base64_encode(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'Base64 编码',
            range
          },
          {
            label: 'base64_decode',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'base64_decode(${1:data})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'Base64 解码',
            range
          },
          {
            label: 'aes_encrypt',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'aes_encrypt(${1:data}, ${2:key}, ${3:iv})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'AES 加密',
            range
          },
          {
            label: 'aes_decrypt',
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: 'aes_decrypt(${1:data}, ${2:key}, ${3:iv})',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'AES 解密',
            range
          }
        );
      }
      // 全局补全
      else {
        // import 语句补全
        suggestions.push(
          {
            label: 'import requests',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'import requests',
            documentation: '导入 requests HTTP 库',
            range
          },
          {
            label: 'import re',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'import re',
            documentation: '导入正则表达式模块',
            range
          },
          {
            label: 'import json',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'import json',
            documentation: '导入 JSON 模块',
            range
          },
          {
            label: 'import base64',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'import base64',
            documentation: '导入 Base64 编码模块',
            range
          },
          {
            label: 'import hashlib',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'import hashlib',
            documentation: '导入哈希算法模块',
            range
          },
          {
            label: 'from urllib.parse import urlencode, quote, unquote',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: 'from urllib.parse import urlencode, quote, unquote',
            documentation: '导入 URL 处理函数',
            range
          }
        );
        
        // 全局变量补全
        suggestions.push(
          {
            label: 'requests',
            kind: monaco.languages.CompletionItemKind.Module,
            insertText: 'requests',
            documentation: 'HTTP 请求库，支持 get, post, put, delete 等方法',
            range
          },
          {
            label: 'share_link_info',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: 'share_link_info',
            documentation: '分享链接信息对象，包含 URL、密码等信息',
            range
          },
          {
            label: 'http',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: 'http',
            documentation: 'HTTP 客户端对象（底层 Java 实现）',
            range
          },
          {
            label: 'logger',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: 'logger',
            documentation: '日志记录器',
            range
          },
          {
            label: 'crypto',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: 'crypto',
            documentation: '加密工具对象，提供 MD5、SHA、AES、Base64 等功能',
            range
          }
        );
        
        // 函数模板补全
        suggestions.push(
          {
            label: 'def parse',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'def parse(share_link_info, http, logger):',
              '    """',
              '    解析单个文件下载链接',
              '    ',
              '    Args:',
              '        share_link_info: 分享链接信息对象',
              '        http: HTTP 客户端',
              '        logger: 日志记录器',
              '    ',
              '    Returns:',
              '        str: 直链下载地址',
              '    """',
              '    url = share_link_info.get_share_url()',
              '    logger.info(f"开始解析: {url}")',
              '    ',
              '    ${0}',
              '    ',
              '    return ""'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '创建 parse 函数模板',
            range
          },
          {
            label: 'def parse_file_list',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'def parse_file_list(share_link_info, http, logger):',
              '    """',
              '    解析文件列表',
              '    ',
              '    Args:',
              '        share_link_info: 分享链接信息对象',
              '        http: HTTP 客户端',
              '        logger: 日志记录器',
              '    ',
              '    Returns:',
              '        list: 文件信息列表',
              '    """',
              '    dir_id = share_link_info.get_other_param("dirId") or "0"',
              '    logger.info(f"解析文件列表，目录ID: {dir_id}")',
              '    ',
              '    file_list = []',
              '    ${0}',
              '    ',
              '    return file_list'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '创建 parse_file_list 函数模板',
            range
          },
          {
            label: 'requests.get example',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'response = requests.get(${1:url}, headers={',
              '    "User-Agent": "Mozilla/5.0"',
              '})',
              'if response.ok:',
              '    data = response.json()',
              '    ${0}'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'requests.get 请求示例',
            range
          },
          {
            label: 'requests.post example',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'response = requests.post(${1:url}, json={',
              '    ${2:"key": "value"}',
              '}, headers={',
              '    "Content-Type": "application/json"',
              '})',
              'if response.ok:',
              '    result = response.json()',
              '    ${0}'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: 'requests.post 请求示例',
            range
          },
          {
            label: 're.search example',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'import re',
              'match = re.search(r\'${1:pattern}\', ${2:text})',
              'if match:',
              '    result = match.group(${3:1})',
              '    ${0}'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '正则表达式搜索示例',
            range
          },
          {
            label: 're.findall example',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: [
              'import re',
              'matches = re.findall(r\'${1:pattern}\', ${2:text})',
              'for match in matches:',
              '    ${0}'
            ].join('\n'),
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            documentation: '正则表达式查找所有匹配示例',
            range
          }
        );
      }

      return { suggestions };
    }
  });
}

/**
 * 从API获取types.js内容并配置
 */
export async function loadTypesFromApi(monaco) {
  try {
    // 先尝试从缓存加载
    const cacheKey = 'playground_types_js';
    const cachedContent = localStorage.getItem(cacheKey);
    if (cachedContent) {
      try {
        monaco.languages.typescript.javascriptDefaults.addExtraLib(
          cachedContent,
          'file:///types.js'
        );
        console.log('从缓存加载types.js成功');
        // 异步更新缓存
        updateTypesJsCache();
        return;
      } catch (error) {
        console.warn('使用缓存的types.js失败，重新加载:', error);
        localStorage.removeItem(cacheKey);
      }
    }
    
    // 从API加载
    const response = await fetch('/v2/playground/types.js');
    if (response.ok) {
      const typesJsContent = await response.text();
      // 缓存到localStorage
      localStorage.setItem(cacheKey, typesJsContent);
      // 添加到类型定义中
      monaco.languages.typescript.javascriptDefaults.addExtraLib(
        typesJsContent,
        'file:///types.js'
      );
      console.log('加载types.js成功并已缓存');
    }
  } catch (error) {
    console.warn('加载types.js失败，使用内置类型定义:', error);
  }
}

/**
 * 异步更新types.js缓存
 */
async function updateTypesJsCache() {
  try {
    const response = await fetch('/v2/playground/types.js');
    if (response.ok) {
      const typesJsContent = await response.text();
      localStorage.setItem('playground_types_js', typesJsContent);
      console.log('types.js缓存已更新');
    }
  } catch (error) {
    console.warn('更新types.js缓存失败:', error);
  }
}

