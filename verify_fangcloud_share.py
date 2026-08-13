#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
亿方云 (FangCloud) 分享链接有效性诊断脚本

用法: 直接 `python3 verify_fangcloud_share.py` 运行, 分享链接已写死在 SHARE_URL
里, 不走命令行传参 (要换链接就直接改这个常量)。

问题背景
--------
仓库里 Java 实现 parser/src/main/java/cn/qaiu/parser/impl/FcTool.java 判断分享
是否有效的方式是直接请求:

    GET https://v2.fangcloud.cn/apps/share_links/info/{uname}

然后看响应 JSON 里的 is_valid 字段。但是:

1. 【接口路径可能已失效/从未存在】
   项目自己抓包留下的记录 web-service/src/main/resources/http-tools/pan-fc.http
   里记录的真实可用请求链路是:

     GET https://v2.fangcloud.com/sharing/{key}
       -> 302 重定向到按分享隔离的子域名
          https://share-<32位hash>.fangcloud.cn/share/{key}
       -> 同时 Set-Cookie: fc_session=...

     之后所有真正的文件接口都要打在这个 share-<hash>.fangcloud.cn 子域名上, 并带
     上第一步拿到的 fc_session cookie, 例如:

     GET https://share-<hash>.fangcloud.cn/apps/files/get_info?scenario=share&item_typed_id=file_xxx
     GET https://share-<hash>.fangcloud.cn/apps/files/download?file_id=xxx&scenario=share&unique_name={key}

   FcTool.java 里的 getShareItem() 从未走过这个"先拿子域名+cookie, 再换接口" 的
   流程, 而是直接把 `/apps/share_links/info/{uname}` 打到中心域名 v2.fangcloud.cn
   上 —— 这个接口路径在项目自己的抓包记录、公开搜索引擎结果、亿方云开放平台
   (https://open.fangcloud.com/doc/api) 文档里都找不到任何佐证, 大概率是已经
   下线或者从来就是猜测出来的路径。一旦这个接口返回非 200/非 JSON/结构不对,
   代码就会直接判定"分享已失效", 而不管分享实际上是否还有效 —— 这正好可以
   解释"无法正确提示分享无效"(即失效判断本身不可信) 的现象。

2. 【正则不认识 /h5/share/ 路径】
   PanDomainTemplate.java 里 FC 的正则是:

     https://v2\.fangcloud\.(com|cn)/(s|share|sharing)/(?<KEY>.+)

   只认 /s/、/share/、/sharing/ 三种路径。而用户给的这条分享链接是移动端 H5
   落地页格式:

     https://v2.fangcloud.cn/h5/share/ded6dc6b9c3672b40b769804bf

   路径是 /h5/share/, 不在正则允许的三选一里, 所以这条链接大概率在 Java 端
   连"这是亿方云分享"都识别不出来, 更谈不上判断有效性。

本脚本做两件事
--------------
A. 纯本地校验现有正则 / 修复后正则 对这条 h5 链接的匹配情况(不需要联网)。
B. 真正发起网络请求, 完整走一遍
     "访问分享落地页 -> 跟随重定向换子域名与 fc_session -> 再探测真实的
     files 接口"
   这条推测中的真实路径, 并把每一步的状态码、最终 URL、拿到的 cookie、
   响应体都打印出来, 方便判断这个分享到底是"真失效"还是"现有代码的接口
   调用方式就是错的"。

注意: 当前开发沙箱的出网被防火墙墙掉了 v2.fangcloud.cn / v2.fangcloud.com,
所以这份脚本本身没有在这个环境里跑通验证过, 需要你在一台能正常访问公网的
机器上运行, 把打印出来的完整输出反馈回来, 才能最终确定 FcTool.java 具体
要怎么改。
"""

import re
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
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}

# 当前 Java 代码里 PanDomainTemplate.FC 使用的正则(只认 s / share / sharing)
CURRENT_FC_REGEX = re.compile(r"https://v2\.fangcloud\.(com|cn)/(s|share|sharing)/(?P<KEY>.+)")

# 建议的修复方向: 额外兼容 /h5/share/ 落地页路径
FIXED_FC_REGEX = re.compile(r"https://v2\.fangcloud\.(com|cn)/(?:h5/)?(s|share|sharing)/(?P<KEY>.+)")


def extract_share_key(url: str) -> str:
    m = re.search(r"/(?:h5/)?(?:s|share|sharing)/([0-9a-zA-Z]+)", url)
    if not m:
        raise ValueError(f"无法从链接中提取 shareKey: {url}")
    return m.group(1)


def check_regex() -> None:
    print("=" * 70)
    print("Step 0. 校验 Java 端 PanDomainTemplate.FC 正则是否匹配这条 h5 分享链接")
    print("=" * 70)
    m1 = CURRENT_FC_REGEX.search(SHARE_URL)
    m2 = FIXED_FC_REGEX.search(SHARE_URL)
    print(f"链接: {SHARE_URL}")
    print(f"现有正则 (只认 s|share|sharing) 是否匹配: {'是' if m1 else '否'}")
    if m1:
        print(f"  -> 提取到的 shareKey = {m1.group('KEY')}")
    print(f"补丁正则 (额外兼容 h5/share) 是否匹配: {'是' if m2 else '否'}")
    if m2:
        print(f"  -> 提取到的 shareKey = {m2.group('KEY')}")
    if not m1 and m2:
        print("\n结论: 这条链接会被现有正则直接漏识别, 根本进不到 FcTool 的分享有效性判断逻辑。")
    print()


def dump_response(resp: requests.Response, label: str) -> None:
    print(f"--- {label} ---")
    print(f"请求: {resp.request.method} {resp.request.url}")
    print(f"状态码: {resp.status_code}")
    print(f"最终URL: {resp.url}")
    if resp.history:
        print("重定向链:")
        for h in resp.history:
            print(f"  {h.status_code} {h.url} -> Location: {h.headers.get('Location')}")
    print(f"当前 session 累积 cookies: {resp.cookies.get_dict()}")
    print(f"Content-Type: {resp.headers.get('Content-Type', '')}")
    body_preview = resp.text[:800].replace("\n", " ")
    print(f"响应体前800字符: {body_preview}")
    print()


def try_json(resp: requests.Response):
    try:
        return resp.json()
    except Exception:
        return None


def main() -> None:
    check_regex()
    share_key = extract_share_key(SHARE_URL)
    print(f"提取到的 shareKey: {share_key}\n")

    session = requests.Session()
    session.headers.update(HEADERS)

    # ---------- Step 1: 复现现有 Java 代码的做法 ----------
    print("=" * 70)
    print("Step 1. 复现现有 FcTool.java 的请求方式 (怀疑是失效判断不可信的根因)")
    print("=" * 70)
    old_api_url = f"https://v2.fangcloud.cn/apps/share_links/info/{share_key}"
    try:
        r = session.get(old_api_url, timeout=15, allow_redirects=True)
        dump_response(r, "现有实现: GET /apps/share_links/info/{uname}")
        data = try_json(r)
        if data is None:
            print("  => 响应不是合法 JSON, 现有 Java 代码此时会直接判定'响应非JSON', "
                  "无法反映分享真实是否有效。\n")
        else:
            print(f"  => JSON 解析成功, is_valid = {data.get('is_valid')}\n")
    except requests.RequestException as e:
        print(f"请求异常: {e}\n")

    # ---------- Step 2: 真正的匿名分享落地页流程 ----------
    print("=" * 70)
    print("Step 2. 模拟浏览器访问分享落地页, 跟随重定向, 换取子域名 + fc_session")
    print("=" * 70)
    landing_candidates = [
        f"https://v2.fangcloud.com/sharing/{share_key}",
        f"https://v2.fangcloud.cn/share/{share_key}",
        SHARE_URL,  # 用户给的原始 h5 链接本身也试一下
    ]
    landed = None
    for url in landing_candidates:
        try:
            r = session.get(url, timeout=15, allow_redirects=True)
            dump_response(r, f"落地页尝试: {url}")
            if r.status_code == 200 and "fangcloud" in r.url:
                landed = r
        except requests.RequestException as e:
            print(f"请求 {url} 异常: {e}\n")

    if landed is None:
        print("三种入口都没有拿到 200 落地页, 请把上面打印的状态码/重定向链贴回来分析。")
        return

    final_url = landed.url
    fc_session = session.cookies.get("fc_session")
    print(f"最终落地 URL: {final_url}")
    print(f"拿到的 fc_session cookie: {fc_session}\n")

    lowered = landed.text
    if any(kw in lowered for kw in ("分享已失效", "分享不存在", "已过期", "链接失效", "文件已被删除")):
        print("落地页 HTML 中直接包含'分享已失效/不存在/已过期'等文案 => 大概率是真失效, "
              "现有代码报'分享已失效'反而是巧合对了。")
    else:
        print("落地页 HTML 未直接包含明显的失效文案, 建议同时用浏览器打开该 URL 人工确认, "
              "并用开发者工具 Network 面板抓一下真实的文件列表 / 下载接口全名, "
              "核对是否与下面 Step 3 探测的路径一致。")

    # ---------- Step 3: 探测真实的 files 接口是否在该子域名下可用 ----------
    print()
    print("=" * 70)
    print("Step 3. 探测 apps/files/get_info 接口在该子域名下的连通性 (无真实 file_id, 仅探测)")
    print("=" * 70)
    base_match = re.match(r"https?://[^/]+", final_url)
    base = base_match.group(0) if base_match else None
    if base:
        probe_url = f"{base}/apps/files/get_info?scenario=share&item_typed_id=file_0"
        try:
            r = session.get(probe_url, timeout=15)
            dump_response(r, "探测: apps/files/get_info")
        except requests.RequestException as e:
            print(f"请求异常: {e}\n")

    # ---------- Step 4: 如果配置了密码, 尝试提交密码解锁 ----------
    if SHARE_PASSWORD:
        print("=" * 70)
        print("Step 4. 分享有密码, 尝试提交密码解锁 (走中心域名的 share_links/access, 仅供对比)")
        print("=" * 70)
        access_url = f"https://v2.fangcloud.cn/apps/share_links/access?unique_name={share_key}"
        try:
            r = session.post(access_url, json={"password": SHARE_PASSWORD}, timeout=15)
            dump_response(r, "POST /apps/share_links/access")
        except requests.RequestException as e:
            print(f"请求异常: {e}\n")

    print("脚本结束。请把以上完整输出发回来, 用于确定 FcTool.java 的具体修复方式 "
          "(大方向通常是: 正则补上 /h5/share/ 路径, 且判断有效性/取文件信息前先访问分享落地页 "
          "拿到重定向后的子域名与 fc_session cookie, 再用这个子域名调用 files 接口, "
          "而不是直接打 v2.fangcloud.cn/apps/share_links/info)。")


if __name__ == "__main__":
    main()
