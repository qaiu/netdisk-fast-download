import * as ts from 'typescript';

/**
 * TypeScript编译器工具类
 * 用于在浏览器中将TypeScript代码编译为ES5 JavaScript
 */

/**
 * 编译TypeScript代码为ES5 JavaScript
 * @param {string} sourceCode - TypeScript源代码
 * @param {string} fileName - 文件名（默认为script.ts）
 * @returns {Object} 编译结果 { success: boolean, code: string, errors: Array }
 */
export function compileToES5(sourceCode, fileName = 'script.ts') {
  try {
    // 编译选项
    const compilerOptions = {
      target: ts.ScriptTarget.ES5,           // 目标版本：ES5
      module: ts.ModuleKind.None,            // 不使用模块系统
      lib: ['lib.es5.d.ts', 'lib.dom.d.ts'], // 包含ES5和DOM类型定义
      removeComments: false,                  // 保留注释
      noEmitOnError: true,                    // 有错误时不生成代码
      noImplicitAny: false,                   // 允许隐式any类型
      strictNullChecks: false,                // 不进行严格的null检查
      suppressImplicitAnyIndexErrors: true,   // 抑制隐式any索引错误
      downlevelIteration: true,               // 支持ES5迭代器降级
      esModuleInterop: true,                  // 启用ES模块互操作性
      allowJs: true,                          // 允许编译JavaScript文件
      checkJs: false                          // 不检查JavaScript文件
    };

    // 执行编译
    const result = ts.transpileModule(sourceCode, {
      compilerOptions,
      fileName,
      reportDiagnostics: true
    });

    // 检查是否有诊断信息（错误/警告）
    const diagnostics = result.diagnostics || [];
    const errors = diagnostics.map(diagnostic => {
      const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
      let location = '';
      if (diagnostic.file && diagnostic.start !== undefined) {
        const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
        location = `(${line + 1},${character + 1})`;
      }
      return {
        message,
        location,
        category: ts.DiagnosticCategory[diagnostic.category],
        code: diagnostic.code
      };
    });

    // 过滤出真正的错误（不包括警告）
    const realErrors = errors.filter(e => e.category === 'Error');

    return {
      success: realErrors.length === 0,
      code: result.outputText || '',
      errors: errors,
      hasWarnings: errors.some(e => e.category === 'Warning'),
      sourceMap: result.sourceMapText
    };
  } catch (error) {
    return {
      success: false,
      code: '',
      errors: [{
        message: error.message || '编译失败',
        location: '',
        category: 'Error',
        code: 0
      }]
    };
  }
}

/**
 * 检查代码是否为TypeScript代码
 * 简单的启发式检查，看是否包含TypeScript特有的语法
 * @param {string} code - 代码字符串
 * @returns {boolean} 是否为TypeScript代码
 */
export function isTypeScriptCode(code) {
  if (!code || typeof code !== 'string') {
    return false;
  }

  // TypeScript特有的语法模式
  const tsPatterns = [
    /:\s*(string|number|boolean|any|void|never|unknown|object)\b/,  // 类型注解
    /interface\s+\w+/,                                                 // interface声明
    /type\s+\w+\s*=/,                                                  // type别名
    /enum\s+\w+/,                                                      // enum声明
    /<\w+>/,                                                           // 泛型
    /implements\s+\w+/,                                                // implements关键字
    /as\s+(string|number|boolean|any|const)/,                        // as类型断言
    /public|private|protected|readonly/,                              // 访问修饰符
    /:\s*\w+\[\]/,                                                     // 数组类型注解
    /\?\s*:/                                                           // 可选属性
  ];

  // 如果匹配任何TypeScript特有模式，则认为是TypeScript代码
  return tsPatterns.some(pattern => pattern.test(code));
}

/**
 * 格式化编译错误信息
 * @param {Array} errors - 错误数组
 * @returns {string} 格式化后的错误信息
 */
export function formatCompileErrors(errors) {
  if (!errors || errors.length === 0) {
    return '';
  }

  return errors.map((error, index) => {
    const prefix = `[${error.category}]`;
    const location = error.location ? ` ${error.location}` : '';
    const code = error.code ? ` (TS${error.code})` : '';
    return `${index + 1}. ${prefix}${location}${code}: ${error.message}`;
  }).join('\n');
}

/**
 * 验证编译后的代码是否为有效的ES5
 * @param {string} code - 编译后的代码
 * @returns {Object} { valid: boolean, error: string }
 */
export function validateES5Code(code) {
  try {
    // 尝试使用Function构造函数验证语法
    // eslint-disable-next-line no-new-func
    new Function(code);
    return { valid: true, error: null };
  } catch (error) {
    return { valid: false, error: error.message };
  }
}

/**
 * 提取代码中的元数据注释
 * @param {string} code - 代码字符串
 * @returns {Object} 元数据对象
 */
export function extractMetadata(code) {
  const metadata = {};
  const metaRegex = /\/\/\s*@(\w+)\s+(.+)/g;
  let match;
  
  while ((match = metaRegex.exec(code)) !== null) {
    const [, key, value] = match;
    metadata[key] = value.trim();
  }
  
  return metadata;
}

export default {
  compileToES5,
  isTypeScriptCode,
  formatCompileErrors,
  validateES5Code,
  extractMetadata
};
