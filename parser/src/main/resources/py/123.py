import requests
import re
import sys
import json
import time
import random
import zlib

def get_timestamp():
    """获取当前时间戳（毫秒）"""
    return str(int(time.time() * 1000))

def crc32(data):
    """计算CRC32并转换为16进制"""
    crc = zlib.crc32(data.encode()) & 0xffffffff
    return format(crc, '08x')

def hex_to_int(hex_str):
    """16进制转10进制"""
    return int(hex_str, 16)

def encode123(url, way, version, timestamp):
    """
    123盘的URL加密算法
    参考C++代码中的encode123函数
    """
    # 生成随机数
    a = int(10000000 * random.randint(1, 10000000) / 10000)
    
    # 字符映射表
    u = "adefghlmyijnopkqrstubcvwsz"
    
    # 将时间戳转换为时间格式
    time_long = int(timestamp) // 1000
    time_struct = time.localtime(time_long)
    time_str = time.strftime("%Y%m%d%H%M", time_struct)
    
    # 根据时间字符串生成g
    g = ""
    for char in time_str:
        digit = int(char)
        if digit == 0:
            g += u[0]
        else:
            # 修正：数字1对应索引0，数字2对应索引1，以此类推
            g += u[digit - 1]
    
    # 计算y值（CRC32的十进制）
    y = str(hex_to_int(crc32(g)))
    
    # 计算最终的CRC32
    final_crc_input = f"{time_long}|{a}|{url}|{way}|{version}|{y}"
    final_crc = str(hex_to_int(crc32(final_crc_input)))
    
    # 返回加密后的URL参数
    return f"?{y}={time_long}-{a}-{final_crc}"

# 与 parser PasswordLoginRisk 保持一致：验证码/风控只提示改用 Authorization，不求解、不重试。
_RISK_KEYWORDS = (
    "验证码", "风控", "安全风险", "安全验证", "安全校验", "滑块", "滑动验证",
    "拼图验证", "短信验证", "微信登录", "微信进行登录", "境外登录", "需要验证", "请验证",
    "请你验证", "进行验证", "完成验证", "二次验证", "人机验证", "操作频繁", "请求过于频繁",
    "请求频繁", "captcha", "recaptcha", "risk control",
)
_AUTH_HINT = (
    "账号密码登录触发了验证码/风控，已停止用户名密码登录（不会尝试验证码）。"
    "请改为配置 Authorization（浏览器登录后的 Bearer token / JWT，"
    "例如 localStorage 的 authorToken、请求头 authorization 或 Cookie 的 sso-token）。"
    "静态配置写在 app-dev.yml 的 auths.ye.authorization（与 auths.ye.token 等价）；"
    "临时请求使用 authType=authorization 或 authType=accesstoken，token 填该令牌（不要重复带 Bearer 前缀）。"
    "说明见 parser/doc/auth-param/。"
)


def format_login_failure(result):
    """把 123 登录失败响应转成用户可见说明。"""
    if not isinstance(result, dict):
        return "登录失败"
    message = result.get("message") or result.get("msg") or ""
    data = result.get("data") if isinstance(result.get("data"), dict) else {}
    data_message = data.get("message") or data.get("msg") or ""
    blob = f"{message} {data_message}".lower()
    risk_field = any(
        key.lower().find(flag) >= 0 and data.get(key) not in (None, False, 0, "", "false", "0")
        for key in data
        for flag in ("captcha", "vcode", "risk", "needverify", "need_verify", "slideverify", "verifycode", "verify_code")
    )
    if risk_field or any(keyword.lower() in blob for keyword in _RISK_KEYWORDS):
        code = result.get("code")
        upstream = message or data_message
        extra = f"code={code}" if code is not None else ""
        if upstream:
            extra = f"{extra}, message={upstream}" if extra else f"message={upstream}"
        return f"{_AUTH_HINT} 上游返回：{extra}" if extra else _AUTH_HINT
    if result.get("code") == 200:
        return "未获取到token"
    return f"登录失败: {message}" if message else "登录失败"


def login_123pan(username, password):
    """登录123盘获取token。成功返回 token，失败返回 None（错误已打印）。"""
    print(f"🔐 正在登录账号: {username}")
    
    login_data = {
        "passport": username,
        "password": password,
        "remember": True
    }
    
    try:
        response = requests.post(
            "https://login.123pan.com/api/user/sign_in",
            json=login_data,
            timeout=30
        )
        result = response.json()
        
        if result.get('code') == 200 and result.get('data', {}).get('token'):
            token = result.get('data', {}).get('token', '')
            print(f"✅ 登录成功！")
            return token
        else:
            error_msg = format_login_failure(result)
            login_123pan.last_error = error_msg
            print(f"❌ {error_msg}")
            return None
    except Exception as e:
        login_123pan.last_error = f"登录请求失败: {e}"
        print(f"❌ {login_123pan.last_error}")
        return None

def get_share_info(share_key, password=''):
    """获取分享信息（不需要登录）"""
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': 'https://www.123pan.com/',
        'Origin': 'https://www.123pan.com',
    }
    
    api_url = f"https://www.123pan.com/b/api/share/get?limit=100&next=1&orderBy=share_id&orderDirection=desc&shareKey={share_key}&SharePwd={password}&ParentFileId=0&Page=1"
    
    try:
        response = requests.get(api_url, headers=headers, timeout=30)
        return response.json()
    except Exception as e:
        print(f"❌ 获取分享信息失败: {e}")
        return None

def get_download_url_android(file_info, token):
    """
    使用Android平台API获取下载链接（关键方法）
    参考C++代码中的逻辑
    """
    # 🔥 关键：使用Android平台的请求头
    headers = {
        'App-Version': '55',
        'platform': 'android',
        'Authorization': f'Bearer {token}',
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36',
        'Content-Type': 'application/json',
    }
    
    # 构建请求数据
    post_data = {
        'driveId': 0,
        'etag': file_info.get('Etag', ''),
        'fileId': file_info.get('FileId'),
        'fileName': file_info.get('FileName', ''),
        's3keyFlag': file_info.get('S3KeyFlag', ''),
        'size': file_info.get('Size'),
        'type': 0
    }
    
    # 🔥 关键：使用encode123加密URL参数
    timestamp = get_timestamp()
    encrypted_params = encode123('/b/api/file/download_info', 'android', '55', timestamp)
    api_url = f"https://www.123pan.com/b/api/file/download_info{encrypted_params}"
    
    print(f"      📡 API URL: {api_url[:80]}...")
    
    try:
        response = requests.post(api_url, json=post_data, headers=headers, timeout=30)
        result = response.json()
        
        print(f"      📥 API响应: code={result.get('code')}, message={result.get('message', 'N/A')}")
        
        if result.get('code') == 0 and 'data' in result:
            download_url = result['data'].get('DownloadUrl') or result['data'].get('DownloadURL')
            return download_url
        else:
            error_msg = result.get('message', '未知错误')
            print(f"      ✗ API返回错误: {error_msg}")
            return None
    except Exception as e:
        print(f"      ✗ 请求失败: {e}")
        import traceback
        traceback.print_exc()
        return None

def start(link, password='', username='', user_password=''):
    """主函数：解析123盘分享链接"""
    result = {
        'code': 200,
        'data': [],
        'need_login': False
    }
    
    # 提取 Share_Key
    patterns = [
        r'/s/(.*?)\.html',
        r'/s/([^/\s]+)',
    ]
    
    share_key = None
    for pattern in patterns:
        matches = re.findall(pattern, link)
        if matches:
            share_key = matches[0]
            break
    
    if not share_key:
        return {
            "code": 201,
            "message": "分享地址错误，无法提取分享密钥"
        }
    
    print(f"📌 分享密钥: {share_key}")
    
    # 如果提供了账号密码，先登录
    token = None
    if username and user_password:
        token = login_123pan(username, user_password)
        if not token:
            return {
                "code": 201,
                "message": getattr(login_123pan, "last_error", None) or "登录失败"
            }
    else:
        print("⚠️  未提供登录信息，某些文件可能无法下载")
    
    # 获取分享信息
    print(f"\n📂 正在获取文件列表...")
    share_data = get_share_info(share_key, password)
    
    if not share_data or share_data.get('code') != 0:
        error_msg = share_data.get('message', '未知错误') if share_data else '请求失败'
        return {
            "code": 201,
            "message": f"获取分享信息失败: {error_msg}"
        }
    
    # 获取文件列表
    if 'data' not in share_data or 'InfoList' not in share_data['data']:
        return {
            "code": 201,
            "message": "返回数据格式错误"
        }
    
    info_list = share_data['data']['InfoList']
    length = len(info_list)
    
    print(f"📁 找到 {length} 个项目\n")
    
    # 遍历文件列表
    for i, file_info in enumerate(info_list):
        file_type = file_info.get('Type', 0)
        file_name = file_info.get('FileName', '')
        
        # 跳过文件夹
        if file_type != 0:
            print(f"[{i+1}/{length}] 跳过文件夹: {file_name}")
            continue
        
        print(f"[{i+1}/{length}] 正在解析: {file_name}")
        
        if not token:
            print(f"      ⚠️  需要登录才能获取下载链接")
            result['need_login'] = True
            continue
        
        # 🔥 使用Android平台API获取下载链接
        print(f"      🤖 使用Android平台API...")
        download_url = get_download_url_android(file_info, token)
        
        if download_url:
            result['data'].append({
                "Name": file_name,
                "Size": file_info.get('Size', 0),
                "DownloadURL": download_url
            })
            print(f"      ✓ 成功获取直链\n")
        else:
            print(f"      ✗ 获取失败\n")
    
    return result

def format_size(size_bytes):
    """格式化文件大小"""
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} PB"

def main():
    """主程序入口"""
    if len(sys.argv) < 2:
        print("=" * 80)
        print("                    123盘直链解析工具 v3.0")
        print("=" * 80)
        print("\n📖 使用方法:")
        print("  python 123.py <分享链接> [选项]")
        print("\n⚙️  选项:")
        print("  --pwd <密码>              分享密码（如果有）")
        print("  --user <账号>             123盘账号")
        print("  --pass <密码>             123盘密码")
        print("\n💡 示例:")
        print('  # 需要登录的分享（推荐）')
        print('  python 123.py "https://www.123pan.com/s/xxxxx" --user "账号" --pass "密码"')
        print()
        print('  # 有分享密码')
        print('  python 123.py "https://www.123pan.com/s/xxxxx" --pwd "分享密码" --user "账号" --pass "密码"')
        print("\n✨ 特性:")
        print("  • 使用Android平台API（完全绕过限制）")
        print("  • 使用123盘加密算法（encode123）")
        print("  • 支持账号密码登录")
        print("  • 无地区限制，无流量限制")
        print("=" * 80)
        sys.exit(1)
    
    link = sys.argv[1]
    password = ''
    username = ''
    user_password = ''
    
    # 解析参数
    i = 2
    while i < len(sys.argv):
        if sys.argv[i] == '--pwd' and i + 1 < len(sys.argv):
            password = sys.argv[i + 1]
            i += 2
        elif sys.argv[i] == '--user' and i + 1 < len(sys.argv):
            username = sys.argv[i + 1]
            i += 2
        elif sys.argv[i] == '--pass' and i + 1 < len(sys.argv):
            user_password = sys.argv[i + 1]
            i += 2
        else:
            i += 1
    
    print("\n" + "=" * 80)
    print("                    开始解析分享链接")
    print("=" * 80)
    print(f"🔗 链接: {link}")
    if password:
        print(f"🔐 分享密码: {password}")
    if username:
        print(f"👤 登录账号: {username}")
    print("=" * 80)
    print()
    
    result = start(link, password, username, user_password)
    
    if result['code'] != 200:
        print(f"\n❌ 错误: {result['message']}")
        sys.exit(1)
    
    if not result['data']:
        print("\n⚠️  没有成功获取到任何文件的直链")
        
        if result.get('need_login'):
            print("\n🔒 该分享需要登录才能下载")
            print("\n请使用以下命令:")
            print(f'  python 123.py "{link}" --user "你的账号" --pass "你的密码"')
        sys.exit(1)
    
    print("\n" + "=" * 80)
    print("                    ✅ 解析成功！")
    print("=" * 80)
    
    for idx, file in enumerate(result['data'], 1):
        print(f"\n📄 文件 {idx}:")
        print(f"   名称: {file['Name']}")
        print(f"   大小: {format_size(file['Size'])} ({file['Size']:,} 字节)")
        print(f"   直链: {file['DownloadURL']}")
        print("-" * 80)
    
    print("\n💾 下载方法:")
    print("\n   使用curl命令:")
    for file in result['data']:
        safe_name = file['Name'].replace('"', '\\"')
        print(f'   curl -L -o "{safe_name}" "{file["DownloadURL"]}"')
    
    print("\n   使用aria2c命令（推荐，多线程）:")
    for file in result['data']:
        safe_name = file['Name'].replace('"', '\\"')
        print(f'   aria2c -x 16 -s 16 -o "{safe_name}" "{file["DownloadURL"]}"')
    
    print("\n💡 提示:")
    print("   • 使用Android平台API，无地区限制")
    print("   • 直链有效期通常为几小时")
    print("   • 推荐使用 aria2c 下载（速度最快）")
    print()

if __name__ == "__main__":
    main()
