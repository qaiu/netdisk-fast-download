import axios from 'axios';

/**
 * 演练场API服务
 */
export const playgroundApi = {
  /**
   * 获取Playground状态
   * @returns {Promise} 状态信息 {enabled, needPassword, authed}
   */
  async getStatus() {
    try {
      const response = await axios.get('/v2/playground/status');
      if (response.data && response.data.data) {
        return response.data.data;
      }
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.msg || error.message || '获取状态失败');
    }
  },

  /**
   * Playground登录
   * @param {string} password - 密码
   * @returns {Promise} 登录结果
   */
  async login(password) {
    try {
      const response = await axios.post('/v2/playground/login', { password });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.msg || error.message || '登录失败');
    }
  },

  /**
   * 测试执行JavaScript代码
   * @param {string} jsCode - JavaScript代码
   * @param {string} shareUrl - 分享链接
   * @param {string} pwd - 密码（可选）
   * @param {string} method - 测试方法：parse/parseFileList/parseById
   * @returns {Promise} 测试结果
   */
  async testScript(jsCode, shareUrl, pwd = '', method = 'parse') {
    try {
      const response = await axios.post('/v2/playground/test', {
        jsCode,
        shareUrl,
        pwd,
        method
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
      const response = await axios.get('/v2/playground/types.js', {
        responseType: 'text'
      });
      return response.data;
    } catch (error) {
      throw new Error(error.response?.data?.error || error.message || '获取types.js失败');
    }
  },

  /**
   * 获取解析器列表
   */
  async getParserList() {
    try {
      const response = await axios.get('/v2/playground/parsers');
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
   */
  async saveParser(jsCode) {
    try {
      const response = await axios.post('/v2/playground/parsers', { jsCode });
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
   */
  async updateParser(id, jsCode, enabled = true) {
    try {
      const response = await axios.put(`/v2/playground/parsers/${id}`, { jsCode, enabled });
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
      const response = await axios.delete(`/v2/playground/parsers/${id}`);
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
      const response = await axios.get(`/v2/playground/parsers/${id}`);
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
  }
};

