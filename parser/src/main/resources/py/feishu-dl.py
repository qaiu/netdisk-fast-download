"""
飞书云盘分享链接解析工具
支持格式：
  文件: https://xxx.feishu.cn/file/TOKEN?from=from_copylink
  文件夹: https://xxx.feishu.cn/drive/folder/TOKEN?from=from_copylink
  ?from=from_copylink 是可选参数，没有分享密码

用法: python feishu-dl.py <飞书分享链接>
"""

import requests
import sys
import re


def download_feishu_file(share_url):
    """
    解析飞书云盘文件分享链接，获取直链下载地址

    :param share_url: 飞书分享链接
    :return: 下载直链 或 None
    """
    # 提取域名和文件token
    match = re.match(
        r'https://([a-zA-Z\d]+)\.feishu\.cn/(file|drive/folder)/([a-zA-Z\d_-]+)',
        share_url
    )
    if not match:
        print(f"无法解析链接: {share_url}")
        return None

    tenant = match.group(1)
    link_type = match.group(2)
    token = match.group(3)
    host = f"{tenant}.feishu.cn"

    print(f"租户: {tenant}")
    print(f"类型: {'文件' if link_type == 'file' else '文件夹'}")
    print(f"Token: {token}")

    if link_type == "drive/folder":
        print("文件夹分享暂不支持直接下载")
        return None

    # 构建下载API URL
    download_api_url = (
        f"https://{host}/space/api/box/stream/download/all/{token}"
        f"?mount_point=explorer"
    )

    headers = {
        'User-Agent': (
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
            'AppleWebKit/537.36 (KHTML, like Gecko) '
            'Chrome/120.0.0.0 Safari/537.36'
        ),
        'Referer': f'https://{host}/',
    }

    print(f"\n请求下载API: {download_api_url}")

    try:
        response = requests.get(
            download_api_url,
            headers=headers,
            allow_redirects=False,
            timeout=30
        )

        if response.status_code in (301, 302):
            download_url = response.headers.get('Location')
            if download_url:
                print(f"解析成功!")
                print(f"下载链接: {download_url}")
                return download_url
            else:
                print("重定向但没有Location头")
        elif response.status_code == 200:
            # 尝试解析JSON响应
            try:
                data = response.json()
                if data.get('code') == 0 and data.get('url'):
                    print(f"解析成功(JSON)!")
                    print(f"下载链接: {data['url']}")
                    return data['url']
            except ValueError:
                pass
            # 如果返回200且不是JSON, API地址本身可能就是下载地址
            return download_api_url
        else:
            print(f"非预期状态码: {response.status_code}")
            print(f"响应: {response.text[:500]}")
    except Exception as e:
        print(f"请求失败: {e}")

    return None


def main():
    if len(sys.argv) < 2:
        print("=" * 60)
        print("        飞书云盘分享链接解析工具")
        print("=" * 60)
        print("\n用法: python feishu-dl.py <飞书分享链接>")
        print("\n示例:")
        print("  python feishu-dl.py "
              "https://xxx.feishu.cn/file/TOKEN")
        print("  python feishu-dl.py "
              "https://xxx.feishu.cn/file/TOKEN?from=from_copylink")
        sys.exit(1)

    url = sys.argv[1]
    download_feishu_file(url)


if __name__ == "__main__":
    main()
