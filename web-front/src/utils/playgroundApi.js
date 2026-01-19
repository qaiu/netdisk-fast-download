import axios from 'axios';

// 创建axios实例，配置携带cookie
const axiosInstance = axios.create({
  withCredentials: true  // 重要：允许跨域请求携带cookie
});

/**
 * 演练场API服务
 */
export const playgroundApi = {
  /**
   * 获取Playground状态（是否需要认证）
   * @returns {Promise} 状态信息
   */
  async getStatus() {
    try {
      const response = await axiosInstance.get('/v2/playground/status');
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '获取状态失败');
    }
  },

  /**
   * Playground登录
   * @param {string} password - 访问密码
   * @returns {Promise} 登录结果
   */
  async login(password) {
    try {
      const response = await axiosInstance.post('/v2/playground/login', { password });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '登录失败');
    }
  },

  /**
   * 测试执行JavaScript/Python代码
   * @param {string} code - 代码
   * @param {string} shareUrl - 分享链接
   * @param {string} pwd - 密码（可选）
   * @param {string} method - 测试方法：parse/parseFileList/parseById
   * @param {string} language - 语言类型：javascript/python
   * @returns {Promise} 测试结果
   */
  async testScript(code, shareUrl, pwd = '', method = 'parse', language = 'javascript') {
    try {
      const response = await axiosInstance.post('/v2/playground/test', {
        jsCode: code, // 兼容后端旧字段名
        code,
        shareUrl,
        pwd,
        method,
        language
      });
      // 框架会自动包装成JsonResult，需要从data字段获取
      if (response.data && response.data.data) {
        return response.data.data;
      }
      // 如果没有包装，直接返回
      return response.data;
    } catch (error) {
      const errorMsg = error.response?.data?.data?.error || 
                      error.response?.data?.error || 
                      error.response?.data?.msg || 
                      error.message || 
                      '测试执行失败';
      throw new Error(errorMsg);
    }
  },

  /**
   * 获取types.js文件内容
   * @returns {Promise<string>} types.js内容
   */
  async getTypesJs() {
    try {
      const response = await axiosInstance.get('/v2/playground/types.js', {
        responseType: 'text'
      });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '获取types.js失败');
    }
  },

  /**
   * 获取types.pyi文件内容（Python类型提示）
   * @returns {Promise<string>} types.pyi内容
   */
  async getTypesPyi() {
    try {
      const response = await axiosInstance.get('/v2/playground/types.pyi', {
        responseType: 'text'
      });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '获取types.pyi失败');
    }
  },

  /**
   * 获取解析器列表
   */
  async getParserList() {
    try {
      const response = await axiosInstance.get('/v2/playground/parsers');
      // 框架会自动包装成JsonResult，需要从data字段获取
      if (response.data && response.data.data) {
        return {
          code: response.data.code || 200,
          data: response.data.data,
          msg: response.data.msg,
          success: response.data.success
        };
      }
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.response?.data?.msg || error.message || '获取解析器列表失败');
    }
  },

  /**
   * 保存解析器
   * @param {string} code - 代码
   * @param {string} language - 语言类型：javascript/python
   * @param {boolean} forceOverwrite - 是否强制覆盖已存在的解析器
   */
  async saveParser(code, language = 'javascript', forceOverwrite = false) {
    try {
      const response = await axiosInstance.post('/v2/playground/parsers', { 
        jsCode: code, // 兼容后端旧字段名
        code,
        language,
        forceOverwrite
      });
      // 框架会自动包装成JsonResult
      if (response.data && response.data.data) {
        return {
          code: response.data.code || 200,
          data: response.data.data,
          msg: response.data.msg,
          success: response.data.success
        };
      }
      return response.data;
    } catch (error) {
      // 检查是否是type已存在的错误（需要覆盖确认）
      const errorData = error.response?.data;
      if (errorData && errorData.existingId && errorData.existingType) {
        // 返回包含existingId的错误信息，供前端显示覆盖确认对话框
        return {
          code: errorData.code || 400,
          msg: errorData.msg || errorData.error || '解析器已存在',
          error: errorData.msg || errorData.error,
          existingId: errorData.existingId,
          existingType: errorData.existingType,
          success: false
        };
      }
      
      const errorMsg = error.response?.data?.data?.error || 
                      error.response?.data?.error || 
                      error.response?.data?.msg || 
                      error.message || 
                      '保存解析器失败';
      throw new Error(errorMsg);
    }
  },

  /**
   * 更新解析器
   * @param {number} id - 解析器ID
   * @param {string} code - 代码
   * @param {boolean} enabled - 是否启用
   * @param {string} language - 语言类型：javascript/python
   */
  async updateParser(id, code, enabled = true, language = 'javascript') {
    try {
      const response = await axiosInstance.put(`/v2/playground/parsers/${id}`, { 
        jsCode: code, // 兼容后端旧字段名
        code,
        enabled,
        language
      });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '更新解析器失败');
    }
  },

  /**
   * 删除解析器
   */
  async deleteParser(id) {
    try {
      const response = await axiosInstance.delete(`/v2/playground/parsers/${id}`);
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '删除解析器失败');
    }
  },

  /**
   * 根据ID获取解析器
   */
  async getParserById(id) {
    try {
      const response = await axiosInstance.get(`/v2/playground/parsers/${id}`);
      // 框架会自动包装成JsonResult
      if (response.data && response.data.data) {
        return {
          code: response.data.code || 200,
          data: response.data.data,
          msg: response.data.msg,
          success: response.data.success
        };
      }
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.response?.data?.msg || error.message || '获取解析器失败');
    }
  },

  /**
   * 获取示例解析器代码
   * @param {string} language - 语言类型：javascript/python
   * @returns {Promise<string>} 示例代码
   */
  async getExampleParser(language = 'javascript') {
    try {
      const response = await axiosInstance.get(`/v2/playground/example/${language}`, {
        responseType: 'text'
      });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || `获取${language}示例失败`);
    }
  },

};
