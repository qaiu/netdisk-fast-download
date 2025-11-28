/**
 * Monaco Editor 代码补全配置工具
 * 基于 types.js 提供完整的代码补全支持
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

