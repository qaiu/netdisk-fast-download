#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
亿方云 (FangCloud) 分享链接有效性验证脚本

用法: 直接 `python3 verify_fangcloud_share.py` 运行, 分享链接已写死在 SHARE_URL
里 (不走命令行传参), 要换链接就直接改这个常量。

根因(已用真实分享链接验证确认)
--------------------------------
GET https://v2.fangcloud.cn/apps/share_links/info/{uname}
返回的是 200 + JSON, 结构形如:

    {
      "process": {
        "is_closed": false,
        "is_expired": false,
        "item": {"type": "file", "id": 45006535173, "name": "..."},
        ...
      }
    }

FcTool.java 原来的实现判断有效性时读的是响应体**顶层**的 "is_valid" 字段
(`json.getBoolean("is_valid")`), 但这个字段根本不存在于该接口的实际响应里
(有效性信息实际上是 process.is_closed / process.is_expired, 且没有任何
名叫 "is_valid" 的字段) —— 所以旧的判断逻辑其实从未真正读取过这个接口的
有效性判断, 而是走的另一套 HTML 抓取 typed_id 的流程, 分享失效时会得到一个
"未匹配到文件id(typed_id)" 这种令人困惑的技术报错, 而不是清晰的"分享已失效"。

修复方案(已同步到 FcTool.java): 解析前先请求一次
https://v2.fangcloud.cn/apps/share_links/info/{uname}, 用
process.is_closed / process.is_expired 判断分享是否有效, 无效则直接返回
"分享已失效或不存在", 有效再继续走原来的 HTML + files/download 流程。

本脚本用于本地复现/回归验证这条判断逻辑, 并顺带跑一遍完整的取直链流程。
"""

import re
import sys
import requests

# 直接写死分享链接, 不通过命令行参数传入
SHARE_URL = "https://v2.fangcloud.cn/h5/share/ded6dc6b9c3672b40b769804bf"

# 如果分享有密码, 在这里填上, 没有就留空字符串
SHARE_PASSWORD = ""

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json, text/html;q=0.9, */*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}

# 修复后的正则, 额外兼容 /h5/share/ 移动端落地页路径 (对齐 PanDomainTemplate.FC)
FC_REGEX = re.compile(r"https://v2\.fangcloud\.(com|cn)/(?:h5/)?(s|share|sharing)/(?P<KEY>.+)")

SHARE_INFO_URL = "https://v2.fangcloud.cn/apps/share_links/info/{key}"
SHARE_URL_PREFIX = "https://v2.fangcloud.com/sharing/{key}"
DOWN_REQUEST_URL = "https://v2.fangcloud.cn/apps/files/download"

TYPED_ID_RE = re.compile(r'id="typed_id"\s+value="file_(\d+)"')


def extract_share_key(url: str) -> str:
    m = FC_REGEX.search(url)
    if not m:
        raise ValueError(f"无法从链接中提取 shareKey (正则不匹配): {url}")
    return m.group("KEY")


def check_is_valid(share_key: str, session: requests.Session) -> bool:
    print("=" * 70)
    print("Step 1. 请求 share_links/info 判断分享有效性 (对齐 FcTool.java 修复后的逻辑)")
    print("=" * 70)
    url = SHARE_INFO_URL.format(key=share_key)
    r = session.get(url, timeout=15)
    print(f"GET {url}")
    print(f"状态码: {r.status_code}")
    try:
        data = r.json()
    except Exception:
        print("响应不是合法JSON => 判定分享已失效或不存在\n")
        return False

    process = data.get("process")
    if not process:
        print("响应中没有 process 字段 => 判定分享已失效或不存在\n")
        return False

    is_closed = bool(process.get("is_closed"))
    is_expired = bool(process.get("is_expired"))
    item = process.get("item")
    print(f"is_closed = {is_closed}, is_expired = {is_expired}, item = {item}")

    if is_closed or is_expired:
        print("=> 分享已失效或不存在\n")
        return False

    print("=> 分享有效\n")
    return True


def get_download_url(share_key: str, pwd: str, session: requests.Session) -> str:
    print("=" * 70)
    print("Step 2. 走原有 HTML + files/download 流程取直链")
    print("=" * 70)
    r = session.get(SHARE_URL_PREFIX.format(key=share_key), timeout=15)
    print(f"GET {SHARE_URL_PREFIX.format(key=share_key)} -> {r.status_code}, 最终URL: {r.url}")

    html = r.text
    if pwd:
        # 加密分享: 提交密码换取跳转后的落地页 (此处仅示意, 具体见 FcTool.java)
        m = re.search(r'name="requesttoken"\s+value="([a-zA-Z0-9_+=]+)"', html)
        if not m:
            raise RuntimeError("未匹配到加密分享的密码输入页面的 requesttoken")
        token = m.group(1)
        r2 = session.post(
            "https://v2.fangcloud.cn/sharing/" + share_key,
            data={"requesttoken": token, "password": pwd},
            timeout=15,
        )
        html = r2.text

    m = TYPED_ID_RE.search(html)
    if not m:
        raise RuntimeError("未匹配到文件id(typed_id), 分享可能是文件夹或页面结构有变化")
    fid = m.group(1)
    print(f"提取到 file_id = {fid}")

    r3 = session.get(
        DOWN_REQUEST_URL,
        params={"file_id": fid, "scenario": "share", "unique_name": share_key},
        timeout=15,
        allow_redirects=False,
    )
    print(f"GET {DOWN_REQUEST_URL} -> {r3.status_code}")
    if r3.status_code in (301, 302) and r3.headers.get("Location"):
        return r3.headers["Location"]
    data = r3.json()
    if not data.get("success"):
        raise RuntimeError(f"取直链失败: {data}")
    return data["download_url"]


def main() -> None:
    share_key = extract_share_key(SHARE_URL)
    print(f"分享链接: {SHARE_URL}")
    print(f"提取到的 shareKey: {share_key}\n")

    session = requests.Session()
    session.headers.update(HEADERS)

    if not check_is_valid(share_key, session):
        print("结论: 分享无效, 不再继续取直链。")
        sys.exit(1)

    try:
        url = get_download_url(share_key, SHARE_PASSWORD, session)
        print(f"\n>>> 直链: {url}")
    except Exception as e:
        print(f"\n取直链失败: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
