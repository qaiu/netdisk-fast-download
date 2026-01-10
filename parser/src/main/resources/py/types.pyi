"""
NFD Python解析器类型存根文件
提供IDE自动补全和类型检查支持
"""

from typing import Dict, List, Optional, Any


class PyShareLinkInfoWrapper:
    """分享链接信息包装器"""
    
    def get_share_url(self) -> str:
        """获取分享URL"""
        ...
    
    def get_share_key(self) -> str:
        """获取分享Key"""
        ...
    
    def get_share_password(self) -> Optional[str]:
        """获取分享密码"""
        ...
    
    def get_type(self) -> str:
        """获取网盘类型"""
        ...
    
    def get_pan_name(self) -> str:
        """获取网盘名称"""
        ...
    
    def get_other_param(self, key: str) -> Optional[Any]:
        """获取其他参数"""
        ...
    
    def get_all_other_params(self) -> Dict[str, Any]:
        """获取所有其他参数"""
        ...
    
    def has_other_param(self, key: str) -> bool:
        """检查是否包含指定参数"""
        ...
    
    def get_other_param_as_string(self, key: str) -> Optional[str]:
        """获取其他参数的字符串值"""
        ...
    
    def get_other_param_as_integer(self, key: str) -> Optional[int]:
        """获取其他参数的整数值"""
        ...
    
    def get_other_param_as_boolean(self, key: str) -> Optional[bool]:
        """获取其他参数的布尔值"""
        ...


class PyHttpResponse:
    """HTTP响应封装"""
    
    def text(self) -> str:
        """获取响应体文本"""
        ...
    
    def body(self) -> str:
        """获取响应体文本（别名）"""
        ...
    
    def json(self) -> Optional[Dict[str, Any]]:
        """解析JSON响应"""
        ...
    
    def status_code(self) -> int:
        """获取HTTP状态码"""
        ...
    
    def header(self, name: str) -> Optional[str]:
        """获取响应头"""
        ...
    
    def headers(self) -> Dict[str, str]:
        """获取所有响应头"""
        ...
    
    def ok(self) -> bool:
        """检查请求是否成功（2xx状态码）"""
        ...
    
    def content(self) -> bytes:
        """获取响应体字节数组"""
        ...
    
    def content_length(self) -> int:
        """获取响应体大小"""
        ...


class PyHttpClient:
    """HTTP客户端"""
    
    def get(self, url: str) -> PyHttpResponse:
        """发起GET请求"""
        ...
    
    def get_with_redirect(self, url: str) -> PyHttpResponse:
        """发起GET请求并跟随重定向"""
        ...
    
    def get_no_redirect(self, url: str) -> PyHttpResponse:
        """发起GET请求但不跟随重定向"""
        ...
    
    def post(self, url: str, data: Any = None) -> PyHttpResponse:
        """发起POST请求"""
        ...
    
    def post_json(self, url: str, json_data: Any = None) -> PyHttpResponse:
        """发起POST请求（JSON数据）"""
        ...
    
    def put(self, url: str, data: Any = None) -> PyHttpResponse:
        """发起PUT请求"""
        ...
    
    def delete(self, url: str) -> PyHttpResponse:
        """发起DELETE请求"""
        ...
    
    def patch(self, url: str, data: Any = None) -> PyHttpResponse:
        """发起PATCH请求"""
        ...
    
    def put_header(self, name: str, value: str) -> 'PyHttpClient':
        """设置请求头"""
        ...
    
    def put_headers(self, headers: Dict[str, str]) -> 'PyHttpClient':
        """批量设置请求头"""
        ...
    
    def remove_header(self, name: str) -> 'PyHttpClient':
        """删除指定请求头"""
        ...
    
    def clear_headers(self) -> 'PyHttpClient':
        """清空所有请求头"""
        ...
    
    def get_headers(self) -> Dict[str, str]:
        """获取所有请求头"""
        ...
    
    def set_timeout(self, seconds: int) -> 'PyHttpClient':
        """设置请求超时时间"""
        ...
    
    @staticmethod
    def url_encode(string: str) -> str:
        """URL编码"""
        ...
    
    @staticmethod
    def url_decode(string: str) -> str:
        """URL解码"""
        ...


class PyLogger:
    """日志记录器"""
    
    def debug(self, message: str, *args) -> None:
        """调试日志"""
        ...
    
    def info(self, message: str, *args) -> None:
        """信息日志"""
        ...
    
    def warn(self, message: str, *args) -> None:
        """警告日志"""
        ...
    
    def error(self, message: str, *args) -> None:
        """错误日志"""
        ...
    
    def is_debug_enabled(self) -> bool:
        """检查是否启用调试级别日志"""
        ...
    
    def is_info_enabled(self) -> bool:
        """检查是否启用信息级别日志"""
        ...


class PyCryptoUtils:
    """加密工具类"""
    
    def md5(self, data: str) -> str:
        """MD5加密（32位小写）"""
        ...
    
    def md5_16(self, data: str) -> str:
        """MD5加密（16位小写）"""
        ...
    
    def sha1(self, data: str) -> str:
        """SHA-1加密"""
        ...
    
    def sha256(self, data: str) -> str:
        """SHA-256加密"""
        ...
    
    def sha512(self, data: str) -> str:
        """SHA-512加密"""
        ...
    
    def base64_encode(self, data: str) -> str:
        """Base64编码"""
        ...
    
    def base64_encode_bytes(self, data: bytes) -> str:
        """Base64编码（字节数组）"""
        ...
    
    def base64_decode(self, data: str) -> str:
        """Base64解码"""
        ...
    
    def base64_decode_bytes(self, data: str) -> bytes:
        """Base64解码（返回字节数组）"""
        ...
    
    def base64_url_encode(self, data: str) -> str:
        """URL安全的Base64编码"""
        ...
    
    def base64_url_decode(self, data: str) -> str:
        """URL安全的Base64解码"""
        ...
    
    def aes_encrypt_ecb(self, data: str, key: str) -> str:
        """AES加密（ECB模式）"""
        ...
    
    def aes_decrypt_ecb(self, data: str, key: str) -> str:
        """AES解密（ECB模式）"""
        ...
    
    def aes_encrypt_cbc(self, data: str, key: str, iv: str) -> str:
        """AES加密（CBC模式）"""
        ...
    
    def aes_decrypt_cbc(self, data: str, key: str, iv: str) -> str:
        """AES解密（CBC模式）"""
        ...
    
    def bytes_to_hex(self, data: bytes) -> str:
        """字节数组转十六进制"""
        ...
    
    def hex_to_bytes(self, hex_string: str) -> bytes:
        """十六进制转字节数组"""
        ...


# 全局变量类型声明
http: PyHttpClient
logger: PyLogger
share_link_info: PyShareLinkInfoWrapper
crypto: PyCryptoUtils


class FileInfo:
    """文件信息"""
    file_name: str
    file_id: str
    file_type: str
    size: int
    size_str: str
    create_time: str
    update_time: str
    create_by: str
    download_count: int
    file_icon: str
    pan_type: str
    parser_url: str
    preview_url: str


def parse(share_link_info: PyShareLinkInfoWrapper, http: PyHttpClient, logger: PyLogger) -> str:
    """
    解析分享链接，获取直链下载地址
    
    这是必须实现的主要解析函数
    
    Args:
        share_link_info: 分享链接信息
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        直链下载地址
    """
    ...


def parse_file_list(share_link_info: PyShareLinkInfoWrapper, http: PyHttpClient, logger: PyLogger) -> List[Dict[str, Any]]:
    """
    解析文件列表
    
    可选实现，用于支持目录分享
    
    Args:
        share_link_info: 分享链接信息
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        文件信息列表
    """
    ...


def parse_by_id(share_link_info: PyShareLinkInfoWrapper, http: PyHttpClient, logger: PyLogger) -> str:
    """
    根据文件ID解析下载链接
    
    可选实现，用于支持按文件ID解析
    
    Args:
        share_link_info: 分享链接信息
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        直链下载地址
    """
    ...
