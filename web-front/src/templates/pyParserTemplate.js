/**
 * Python 解析器模板
 * 包含解析器的基础模板代码
 */

/**
 * 生成 Python 解析器模板代码
 * @param {string} name - 解析器名称
 * @param {string} identifier - 标识符
 * @param {string} author - 作者
 * @param {string} match - URL匹配模式
 * @returns {string} Python模板代码
 */
export const generatePyTemplate = (name, identifier, author, match) => {
  const type = identifier.toLowerCase().replace(/[^a-z0-9]/g, '_');
  const displayName = name;
  const description = `使用Python实现的${name}解析器`;
  
  return `# ==UserScript==
# @name         ${name}
# @type         ${type}
# @displayName  ${displayName}
# @description  ${description}
# @match        ${match || 'https?://example.com/s/(?<KEY>\\\\w+)'}
# @author       ${author || 'yourname'}
# @version      1.0.0
# ==/UserScript==

"""
${name}解析器 - Python实现
使用GraalPy运行，提供与JavaScript解析器相同的功能

可用模块：
- requests: HTTP请求库 (已内置，支持 get/post/put/delete 等)
- re: 正则表达式
- json: JSON处理
- base64: Base64编解码
- hashlib: 哈希算法

内置对象：
- share_link_info: 分享链接信息
- http: 底层HTTP客户端
- logger: 日志记录器
- crypto: 加密工具 (md5/sha1/sha256/aes/base64)
"""

import requests
import re
import json


def parse(share_link_info, http, logger):
    """
    解析单个文件下载链接
    
    Args:
        share_link_info: 分享链接信息对象
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        str: 直链下载地址
    """
    url = share_link_info.get_share_url()
    logger.info(f"开始解析: {url}")
    
    # 使用 requests 库发起请求（推荐）
    response = requests.get(url, headers={
        "Referer": url
    })
    
    if not response.ok:
        raise Exception(f"请求失败: {response.status_code}")
    
    html = response.text
    
    # 示例：使用正则表达式提取下载链接
    # match = re.search(r'download_url["\\':]\s*["\\']([^"\\'>]+)', html)
    # if match:
    #     return match.group(1)
    
    return "https://example.com/download/file.zip"


def parse_file_list(share_link_info, http, logger):
    """
    解析文件列表（可选）
    
    Args:
        share_link_info: 分享链接信息对象
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        list: 文件信息列表
    """
    dir_id = share_link_info.get_other_param("dirId") or "0"
    logger.info(f"解析文件列表，目录ID: {dir_id}")
    
    file_list = []
    
    return file_list
`;
};

/**
 * Python 解析器的默认空白模板
 */
export const PY_EMPTY_TEMPLATE = `# ==UserScript==
# @name         新解析器
# @type         new_parser
# @displayName  新解析器
# @description  解析器描述
# @match        https?://example.com/s/(?<KEY>\\w+)
# @author       yourname
# @version      1.0.0
# ==/UserScript==

import requests
import re
import json


def parse(share_link_info, http, logger):
    """解析单个文件下载链接"""
    url = share_link_info.get_share_url()
    logger.info(f"开始解析: {url}")
    
    # 在这里编写你的解析逻辑
    
    return ""
`;

/**
 * Python HTTP 请求示例模板
 */
export const PY_HTTP_EXAMPLE = `# HTTP 请求示例

import requests
import json

# GET 请求
response = requests.get("https://api.example.com/data")
if response.ok:
    data = response.json()
    logger.info("获取数据成功")

# POST 请求（表单数据）
form_data = {
    "key": "value",
    "name": "test"
}
post_response = requests.post("https://api.example.com/submit", data=form_data)

# POST 请求（JSON数据）
json_data = {"id": 1, "name": "test"}
json_response = requests.post(
    "https://api.example.com/api",
    json=json_data,
    headers={"Content-Type": "application/json"}
)

# 自定义请求头
custom_headers = {
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://example.com"
}
custom_response = requests.get("https://api.example.com/data", headers=custom_headers)

# 会话保持 Cookie
session = requests.Session()
session.get("https://example.com/login")  # 获取 Cookie
session.post("https://example.com/api")   # 自动带上 Cookie
`;

/**
 * Python 正则表达式示例
 */
export const PY_REGEX_EXAMPLE = `# 正则表达式示例

import re

html = response.text

# 匹配下载链接
download_match = re.search(r'href=["\\']([^"\\']*.zip)["\\'\\']', html)
if download_match:
    download_url = download_match.group(1)

# 匹配JSON数据
json_match = re.search(r'var data = (\\{[^}]+\\})', html)
if json_match:
    data = json.loads(json_match.group(1))

# 查找所有匹配项
all_links = re.findall(r'href=["\\']([^"\\']]+)["\\'\\']', html)

# 使用命名分组
pattern = r'<a href="(?P<url>[^"]+)">(?P<text>[^<]+)</a>'
for match in re.finditer(pattern, html):
    url = match.group('url')
    text = match.group('text')
`;

/**
 * Python 安全提示
 */
export const PY_SECURITY_NOTICE = `# ⚠️ Python 安全限制说明
#
# 以下操作被禁止（安全策略限制）：
# - os.system()     系统命令执行
# - os.popen()      进程创建
# - os.remove()     删除文件
# - os.rmdir()      删除目录
# - subprocess.*    子进程操作
# - open() 文件写入 (read模式允许)
#
# 允许的操作：
# - requests.*      网络请求
# - re.*           正则表达式
# - json.*         JSON处理
# - base64.*       Base64编解码
# - hashlib.*      哈希算法
# - os.getcwd()    获取当前目录
# - os.path.*      路径操作
`;

export default {
  generatePyTemplate,
  PY_EMPTY_TEMPLATE,
  PY_HTTP_EXAMPLE,
  PY_REGEX_EXAMPLE,
  PY_SECURITY_NOTICE
};
