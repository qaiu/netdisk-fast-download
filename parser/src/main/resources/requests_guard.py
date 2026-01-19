"""
requests_guard.py - 网络请求安全卫士
对 requests, urllib 等网络库做猴子补丁，阻断本地及危险地址的访问
用法：在程序最早 import 本模块即可全局生效

功能：
1. 拦截 requests 库的所有 HTTP 请求
2. 检测和阻止访问本地地址（127.0.0.1, localhost 等）
3. 检测和阻止访问私网地址（10.0.0.0, 172.16.0.0, 192.168.0.0, 等）
4. 提供详细的审计日志

作者: QAIU
版本: 1.0.0
"""

import socket
import sys
from urllib.parse import urlparse


# ===== IP 地址判断工具 =====

# 常见内网/危险网段（可按需增删）
PRIVATE_NETS = [
    "127.0.0.0/8",        # 本地回环
    "10.0.0.0/8",         # A 类私网
    "172.16.0.0/12",      # B 类私网
    "192.168.0.0/16",     # C 类私网
    "0.0.0.0/8",          # 0.x.x.x
    "169.254.0.0/16",     # Link-local
    "224.0.0.0/4",        # 多播地址
    "240.0.0.0/4",        # 预留地址
]

# 危险端口列表（常见网络服务端口）
DANGEROUS_PORTS = [
    22,     # SSH
    25,     # SMTP
    53,     # DNS
    3306,   # MySQL
    5432,   # PostgreSQL
    6379,   # Redis
    8000, 8001, 8080, 8888,  # 常见开发服务器端口
    27017,  # MongoDB
]


def _ip_in_nets(ip_str: str) -> bool:
    """判断 IP 是否落在 PRIVATE_NETS 中的任一 CIDR"""
    try:
        from ipaddress import ip_address, ip_network
        addr = ip_address(ip_str)
        return any(addr in ip_network(cidr) for cidr in PRIVATE_NETS)
    except (ValueError, ImportError):
        # 如果解析失败（非IP地址）或模块不可用，返回False（不是私网IP）
        return False


def _hostname_resolves_to_private(hostname: str) -> bool:
    """解析域名并判断解析结果是否落在私网"""
    try:
        _, _, ips = socket.gethostbyname_ex(hostname)
        return any(_ip_in_nets(ip) for ip in ips)
    except (OSError, socket.error):
        # 解析失败（如网络问题、DNS不可用）：允许访问，不视为私网
        # 仅当成功解析且落在私网时才拦截
        return False


def _is_dangerous_port(port):
    """判断是否为危险端口"""
    return port in DANGEROUS_PORTS


# ===== 日志工具 =====

class GuardLogger:
    """网络请求卫士日志记录器"""
    
    # 用于去重的最近请求缓存（避免重复日志）
    _recent_requests = set()
    _max_cache_size = 100
    
    @staticmethod
    def audit(level, message):
        """输出审计日志"""
        timestamp = _get_timestamp()
        log_msg = f"[{timestamp}] [Guard-{level}] {message}"
        print(log_msg)
        # 可以在这里添加文件日志、数据库日志等
        sys.stdout.flush()
    
    @staticmethod
    def allow(method, url):
        """记录允许的请求（带去重）"""
        request_key = f"{method.upper()}:{url}"
        if request_key not in GuardLogger._recent_requests:
            GuardLogger._recent_requests.add(request_key)
            # 限制缓存大小
            if len(GuardLogger._recent_requests) > GuardLogger._max_cache_size:
                GuardLogger._recent_requests.clear()
            GuardLogger.audit("ALLOW", f"{method.upper():6} {url}")
    
    @staticmethod
    def block(method, url, reason):
        """记录被阻止的请求"""
        GuardLogger.audit("BLOCK", f"{method.upper():6} {url} - {reason}")


def _get_timestamp():
    """获取当前时间戳"""
    try:
        from datetime import datetime
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    except ImportError:
        return ""


# ===== requests 库猴子补丁 =====

def _patch_requests():
    """为 requests 库应用猴子补丁"""
    try:
        import requests
        from requests import models
        
        # 备份原始的 request 方法
        _orig_request = requests.api.request
        _orig_session_request = requests.Session.request
        
        # 备份高层快捷函数（在修改之前）
        _orig_methods = {}
        for method in ("get", "post", "put", "patch", "delete", "head", "options"):
            _orig_methods[method] = getattr(requests, method, None)
        
        def _safe_request(method, url, **kwargs):
            """安全的 request 包装函数"""
            _validate_url(method, url)
            GuardLogger.allow(method, url)
            return _orig_request(method, url, **kwargs)
        
        def _safe_session_request(self, method, url, **kwargs):
            """安全的 Session.request 包装函数"""
            _validate_url(method, url)
            GuardLogger.allow(method, url)
            return _orig_session_request(self, method, url, **kwargs)
        
        # 应用猴子补丁
        requests.api.request = _safe_request
        requests.Session.request = _safe_session_request
        
        # 为了兼容高层快捷函数 get/post/...
        for method_name, original_method in _orig_methods.items():
            if original_method:
                # 创建闭包保存当前方法名和原始方法
                def make_safe_method(m, orig_func):
                    def safe_method(url, **kwargs):
                        _validate_url(m, url)
                        GuardLogger.allow(m, url)
                        return orig_func(url, **kwargs)
                    return safe_method
                setattr(requests, method_name, make_safe_method(method_name, original_method))
        
        GuardLogger.audit("INFO", "requests 库猴子补丁加载成功，已启用网络请求安全拦截")
        return True
        
    except ImportError:
        GuardLogger.audit("DEBUG", "requests 库未安装，跳过补丁")
        return False
    except Exception as e:
        GuardLogger.audit("ERROR", f"requests 库补丁加载失败: {str(e)}")
        return False


# ===== urllib 库猴子补丁 =====

def _patch_urllib():
    """为 urllib 库应用猴子补丁"""
    try:
        import urllib.request
        import urllib.error
        
        # 备份原始方法
        _orig_urlopen = urllib.request.urlopen
        
        def _safe_urlopen(url, *args, **kwargs):
            """安全的 urlopen 包装函数"""
            if isinstance(url, str):
                _validate_url("GET", url)
                GuardLogger.allow("GET", url)
            elif hasattr(url, 'get_full_url'):
                # 处理 Request 对象
                full_url = url.get_full_url()
                _validate_url(url.get_method(), full_url)
                GuardLogger.allow(url.get_method(), full_url)
            
            return _orig_urlopen(url, *args, **kwargs)
        
        # 应用猴子补丁
        urllib.request.urlopen = _safe_urlopen
        
        GuardLogger.audit("INFO", "urllib 库猴子补丁加载成功")
        return True
        
    except ImportError:
        GuardLogger.audit("DEBUG", "urllib 库未安装或不可用，跳过补丁")
        return False
    except Exception as e:
        GuardLogger.audit("ERROR", f"urllib 库补丁加载失败: {str(e)}")
        return False


# ===== 核心验证函数 =====

def _validate_url(method: str, url: str):
    """验证 URL 是否安全"""
    if not isinstance(url, str):
        raise ValueError(f"[Guard] 非法 URL 类型：{type(url)}")
    
    if not url or len(url) == 0:
        raise ValueError("[Guard] URL 不能为空")
    
    # 解析 URL
    try:
        parsed = urlparse(url)
    except Exception as e:
        raise ValueError(f"[Guard] 无法解析 URL：{url} - {str(e)}")
    
    scheme = parsed.scheme.lower()
    host = parsed.hostname
    port = parsed.port
    
    # 检查协议（仅允许 http/https）
    if scheme not in ("http", "https"):
        GuardLogger.block(method, url, f"不允许的协议: {scheme}")
        raise PermissionError(f"[Guard] 禁止访问不安全的协议：{scheme}://")
    
    if not host:
        GuardLogger.block(method, url, "无法解析主机名")
        raise ValueError(f"[Guard] 无法解析 URL 中的主机名：{url}")
    
    # 1. 快速检查本地地址
    host_lower = host.lower()
    if host_lower in ("localhost", "127.0.0.1", "::1", "[::1]"):
        GuardLogger.block(method, url, "本地地址")
        raise PermissionError(f"[Guard] 禁止访问本地地址：{url}")
    
    # 2. 检查危险端口
    if port and _is_dangerous_port(port):
        GuardLogger.block(method, url, f"危险端口 {port}")
        raise PermissionError(f"[Guard] 禁止访问危险端口 {port}：{url}")
    
    # 3. 检查是否为 IP 地址或解析后落在私网网段
    try:
        # 判断 host 是否为纯 IP 地址（仅包含数字、点、冒号）
        is_ip_format = all(c.isdigit() or c in '.:-[]' for c in host)
        
        if is_ip_format:
            # 如果是 IP 格式，检查是否落在私网段
            if _ip_in_nets(host):
                GuardLogger.block(method, url, "私网IP地址")
                raise PermissionError(f"[Guard] 禁止访问私网/危险地址：{url}")
        else:
            # 如果是域名，解析后检查是否指向私网
            if _hostname_resolves_to_private(host):
                GuardLogger.block(method, url, "域名解析到私网")
                raise PermissionError(f"[Guard] 禁止访问私网/危险地址（域名解析）：{url}")
    
    except PermissionError:
        raise  # 重新抛出 PermissionError
    except Exception as e:
        # 其他异常（如 DNS 解析异常）允许通过，仅记录警告
        GuardLogger.audit("WARN", f"地址检查异常（已允许）: {url} - {str(e)}")


# ===== 初始化和全局补丁应用 =====

def apply_all_patches():
    """应用所有网络库的补丁"""
    print("[Guard] 正在初始化网络请求安全卫士...")
    
    patches_applied = []
    
    # 应用 requests 补丁
    if _patch_requests():
        patches_applied.append("requests")
    
    # 应用 urllib 补丁
    if _patch_urllib():
        patches_applied.append("urllib")
    
    if patches_applied:
        msg = f"[Guard] 成功应用 {len(patches_applied)} 个网络库补丁: {', '.join(patches_applied)}"
        GuardLogger.audit("INFO", msg)
    else:
        GuardLogger.audit("WARN", "[Guard] 没有可用的网络库可以补丁")


# ===== 模块初始化 =====

# 在模块加载时自动应用所有补丁
apply_all_patches()

# 暴露公共接口
__all__ = [
    'GuardLogger',
    'apply_all_patches',
    'PRIVATE_NETS',
    'DANGEROUS_PORTS',
]
