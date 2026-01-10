# ==UserScript==
# @name         示例Python解析器
# @type         example_py_parser
# @displayName  示例网盘(Python)
# @match        https?://example\.com/s/(?P<KEY>\w+)(?:\?pwd=(?P<PWD>\w+))?
# @description  Python解析器示例，展示如何编写Python网盘解析器
# @author       QAIU
# @version      1.0.0
# ==/UserScript==

"""
Python解析器示例

可用的全局对象:
- http: HTTP客户端 (PyHttpClient)
- logger: 日志对象 (PyLogger)
- share_link_info: 分享信息 (PyShareLinkInfoWrapper)
- crypto: 加密工具 (PyCryptoUtils)

必须实现的函数:
- parse(share_link_info, http, logger): 解析下载链接，返回下载URL字符串

可选实现的函数:
- parse_file_list(share_link_info, http, logger): 解析文件列表，返回文件信息列表
- parse_by_id(share_link_info, http, logger): 根据文件ID解析下载链接
"""


def parse(share_link_info, http, logger):
    """
    解析分享链接，获取直链下载地址
    
    参数:
        share_link_info: 分享信息对象
            - get_share_url(): 获取分享URL
            - get_share_key(): 获取分享Key
            - get_share_password(): 获取分享密码
            - get_type(): 获取网盘类型
        http: HTTP客户端
            - get(url): GET请求
            - post(url, data): POST请求
            - put_header(name, value): 设置请求头
            - set_timeout(seconds): 设置超时时间
        logger: 日志对象
            - info(msg): 信息日志
            - debug(msg): 调试日志
            - warn(msg): 警告日志
            - error(msg): 错误日志
    
    返回:
        str: 直链下载地址
    """
    # 获取分享信息
    share_url = share_link_info.get_share_url()
    share_key = share_link_info.get_share_key()
    share_password = share_link_info.get_share_password()
    
    logger.info(f"开始解析: {share_url}")
    logger.info(f"分享Key: {share_key}")
    
    # 设置请求头
    http.put_header("Referer", share_url)
    
    # 发起GET请求获取页面内容
    response = http.get(share_url)
    
    if not response.ok():
        logger.error(f"请求失败: {response.status_code()}")
        raise Exception(f"请求失败: {response.status_code()}")
    
    html = response.text()
    logger.debug(f"响应长度: {len(html)}")
    
    # 示例：从响应中提取下载链接
    # 实际解析逻辑根据具体网盘API实现
    
    # 演示使用加密工具
    # md5_hash = crypto.md5(share_key)
    # logger.info(f"MD5: {md5_hash}")
    
    # 返回模拟的下载链接
    return f"https://example.com/download/{share_key}"


def parse_file_list(share_link_info, http, logger):
    """
    解析文件列表
    
    返回:
        list: 文件信息列表，每个元素是字典，包含:
            - file_name: 文件名
            - file_id: 文件ID
            - file_type: 文件类型
            - size: 文件大小(字节)
            - pan_type: 网盘类型
            - parser_url: 解析URL
    """
    share_url = share_link_info.get_share_url()
    share_key = share_link_info.get_share_key()
    
    logger.info(f"获取文件列表: {share_url}")
    
    # 示例返回
    return [
        {
            "file_name": "示例文件1.txt",
            "file_id": "file_001",
            "file_type": "file",
            "size": 1024,
            "pan_type": "example_py_parser",
            "parser_url": f"/parser?type=example_py_parser&key={share_key}&fileId=file_001"
        },
        {
            "file_name": "示例文件2.zip",
            "file_id": "file_002",
            "file_type": "file",
            "size": 2048,
            "pan_type": "example_py_parser",
            "parser_url": f"/parser?type=example_py_parser&key={share_key}&fileId=file_002"
        }
    ]


def parse_by_id(share_link_info, http, logger):
    """
    根据文件ID解析下载链接
    
    返回:
        str: 直链下载地址
    """
    file_id = share_link_info.get_other_param("fileId")
    share_key = share_link_info.get_share_key()
    
    logger.info(f"按ID解析: fileId={file_id}, shareKey={share_key}")
    
    # 返回模拟的下载链接
    return f"https://example.com/download/{share_key}/{file_id}"
