/**
 * 解析器模板统一导出
 * 提供 JavaScript 和 Python 解析器模板的统一接口
 */

import {
  generateJsTemplate,
  JS_EMPTY_TEMPLATE,
  JS_HTTP_EXAMPLE,
  JS_REGEX_EXAMPLE
} from './jsParserTemplate';

import {
  generatePyTemplate,
  PY_EMPTY_TEMPLATE,
  PY_HTTP_EXAMPLE,
  PY_REGEX_EXAMPLE,
  PY_SECURITY_NOTICE
} from './pyParserTemplate';

/**
 * 根据语言生成模板代码
 * @param {string} name - 解析器名称
 * @param {string} identifier - 标识符
 * @param {string} author - 作者
 * @param {string} match - URL匹配模式
 * @param {string} language - 语言类型 ('javascript' | 'python')
 * @returns {string} 模板代码
 */
export const generateTemplate = (name, identifier, author, match, language = 'javascript') => {
  if (language === 'python') {
    return generatePyTemplate(name, identifier, author, match);
  }
  return generateJsTemplate(name, identifier, author, match);
};

/**
 * 获取默认空白模板
 * @param {string} language - 语言类型
 * @returns {string} 空白模板代码
 */
export const getEmptyTemplate = (language = 'javascript') => {
  if (language === 'python') {
    return PY_EMPTY_TEMPLATE;
  }
  return JS_EMPTY_TEMPLATE;
};

/**
 * 获取 HTTP 请求示例
 * @param {string} language - 语言类型
 * @returns {string} HTTP 示例代码
 */
export const getHttpExample = (language = 'javascript') => {
  if (language === 'python') {
    return PY_HTTP_EXAMPLE;
  }
  return JS_HTTP_EXAMPLE;
};

/**
 * 获取正则表达式示例
 * @param {string} language - 语言类型
 * @returns {string} 正则表达式示例代码
 */
export const getRegexExample = (language = 'javascript') => {
  if (language === 'python') {
    return PY_REGEX_EXAMPLE;
  }
  return JS_REGEX_EXAMPLE;
};

// 导出所有模板
export {
  // JavaScript
  generateJsTemplate,
  JS_EMPTY_TEMPLATE,
  JS_HTTP_EXAMPLE,
  JS_REGEX_EXAMPLE,
  // Python
  generatePyTemplate,
  PY_EMPTY_TEMPLATE,
  PY_HTTP_EXAMPLE,
  PY_REGEX_EXAMPLE,
  PY_SECURITY_NOTICE
};

export default {
  generateTemplate,
  getEmptyTemplate,
  getHttpExample,
  getRegexExample,
  // JavaScript
  generateJsTemplate,
  JS_EMPTY_TEMPLATE,
  JS_HTTP_EXAMPLE,
  JS_REGEX_EXAMPLE,
  // Python
  generatePyTemplate,
  PY_EMPTY_TEMPLATE,
  PY_HTTP_EXAMPLE,
  PY_REGEX_EXAMPLE,
  PY_SECURITY_NOTICE
};
