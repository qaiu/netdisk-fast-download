#!/usr/bin/env python3
"""
飞书公开分享 直链解析 + 批量下载 (aria2/Motrix)
支持: 单文件链接 / 文件夹链接(递归子目录)

用法:
  python feishu_dl.py <链接>                     # 推送到 Motrix
  python feishu_dl.py <链接> -d D:/Downloads      # 指定下载目录
  python feishu_dl.py <链接> --list               # 仅列出文件,不下载
  python feishu_dl.py <链接> --aria2c              # 输出 aria2c 命令行
"""

import sys, os, re, json, uuid, ssl, gzip, argparse
import http.cookiejar
import urllib.request, urllib.error
from urllib.parse import unquote, quote

# ─── Motrix aria2 RPC 默认配置 ──────────────────────────
ARIA2_RPC_URL = "http://localhost:16800/jsonrpc"
ARIA2_SECRET  = "motrix"
# ────────────────────────────────────────────────────────

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")

# 飞书 obj_type 映射 (type=12 上传文件可下载, type=0 文件夹可递归)
OBJ_TYPES = {
    0: "📁 文件夹", 2: "📝 旧版文档", 3: "📊 表格", 8: "🧠 思维导图",
    11: "📽 幻灯片", 12: "📄 文件", 22: "📝 新版文档", 30: "📋 画板",
    44: "📊 多维表格", 84: "📑 知识库", 123: "❓ 未知", 124: "❓ 未知",
}

# v3 列表 API 支持的 obj_type
LIST_OBJ_TYPES = [0, 2, 22, 44, 3, 30, 8, 11, 12, 84, 123, 124]


# ─── 网络工具 ────────────────────────────────────────────

def _ctx():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx

def make_opener(jar):
    return urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=_ctx()),
        urllib.request.HTTPCookieProcessor(jar),
    )

def decode_body(resp):
    data = resp.read()
    if resp.headers.get("Content-Encoding") == "gzip":
        data = gzip.decompress(data)
    return data.decode("utf-8", errors="replace")

def cookie_string(jar):
    return "; ".join(f"{c.name}={c.value}" for c in jar)

def human_size(n):
    for u in ("B", "KB", "MB", "GB"):
        if n < 1024: return f"{n:.1f} {u}"
        n /= 1024
    return f"{n:.1f} TB"


# ─── 飞书核心 API ────────────────────────────────────────

def parse_share_url(url):
    """返回 (tenant, token, link_type:'file'|'folder')"""
    m = re.match(r'https://([^.]+)\.feishu\.cn/file/([A-Za-z0-9_-]+)', url)
    if m: return m.group(1), m.group(2), "file"
    m = re.match(r'https://([^.]+)\.feishu\.cn/drive/folder/([A-Za-z0-9_-]+)', url)
    if m: return m.group(1), m.group(2), "folder"
    return None, None, None


def fetch_session(share_url):
    """访问分享页拿匿名 session cookie"""
    jar = http.cookiejar.CookieJar()
    opener = make_opener(jar)
    req = urllib.request.Request(share_url)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "text/html,*/*")
    opener.open(req, timeout=15).read()
    return jar


def list_folder(tenant, folder_token, jar, page_label=""):
    """
    v3 API 列出文件夹内容 (单页)
    GET /space/api/explorer/v3/children/list/?token=xxx&length=50&...
    """
    base = f"https://{tenant}.feishu.cn"
    params = ["length=50", "asc=1", "rank=5", f"token={folder_token}"]
    for t in LIST_OBJ_TYPES:
        params.append(f"obj_type={t}")
    if page_label:
        params.append(f"last_label={quote(page_label, safe='')}")

    url = f"{base}/space/api/explorer/v3/children/list/?{'&'.join(params)}"
    opener = make_opener(jar)
    req = urllib.request.Request(url)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "application/json, text/plain, */*")
    req.add_header("Referer", f"{base}/drive/folder/{folder_token}")

    resp = opener.open(req, timeout=15)
    data = json.loads(decode_body(resp))
    if data.get("code") != 0:
        raise RuntimeError(f"API error: {data.get('msg')}")

    d = data["data"]
    nodes = d.get("entities", {}).get("nodes", {})
    node_list = d.get("node_list", [])

    items = []
    for nid in node_list:
        node = nodes.get(nid, {})
        obj_type = node.get("type", -1)
        obj_token = node.get("obj_token", "")
        name = node.get("name", "unknown")
        extra = node.get("extra", {})
        try:   size = int(extra.get("size", "0"))
        except: size = 0
        items.append({
            "name": name, "obj_token": obj_token, "type": obj_type,
            "size": size, "url": node.get("url", ""),
            "is_folder": obj_type == 0,
            "type_name": OBJ_TYPES.get(obj_type, f"❓ type={obj_type}"),
        })

    # 排除文件夹自身节点
    items = [it for it in items if it["obj_token"] != folder_token]
    return items, d.get("has_more", False), d.get("last_label", "")


def list_folder_all(tenant, folder_token, jar):
    """分页获取文件夹全部内容"""
    all_items, label = [], ""
    while True:
        items, has_more, label = list_folder(tenant, folder_token, jar, label)
        all_items.extend(items)
        if not has_more: break
    return all_items


def walk_folder(tenant, folder_token, jar, prefix="", depth=0):
    """递归遍历, 返回扁平列表 [{..., path:"a/b/file.txt"}]"""
    if depth > 10:  # 防止无限递归
        return []
    items = list_folder_all(tenant, folder_token, jar)
    result = []
    for it in items:
        if it["is_folder"]:
            sub = walk_folder(tenant, it["obj_token"], jar,
                              prefix=f"{prefix}{it['name']}/", depth=depth+1)
            result.extend(sub)
        else:
            it["path"] = f"{prefix}{it['name']}"
            result.append(it)
    return result


def probe_file(tenant, obj_token, jar, referer):
    """Range 探测文件名 + 大小 (只取1字节)"""
    dl_url = f"https://{tenant}.feishu.cn/space/api/box/stream/download/all/{obj_token}"
    opener = make_opener(jar)
    req = urllib.request.Request(dl_url)
    req.add_header("User-Agent", UA)
    req.add_header("Referer", referer)
    req.add_header("Range", "bytes=0-0")

    resp = opener.open(req, timeout=15)
    cd = resp.headers.get("Content-Disposition", "")
    cr = resp.headers.get("Content-Range", "")
    resp.read()

    filename = ""
    m = re.search(r"filename\*=UTF-8''(.+?)(?:;|$)", cd)
    if m: filename = unquote(m.group(1).strip())
    if not filename:
        m = re.search(r'filename="?([^";]+)"?', cd)
        if m: filename = unquote(m.group(1).strip())

    total = 0
    m = re.search(r'/(\d+)', cr)
    if m: total = int(m.group(1))
    return filename, total


# ─── aria2 RPC ───────────────────────────────────────────

def aria2_add(dl_url, cs, referer, filename, out_dir=None):
    opts = {"header": [f"Cookie: {cs}", f"Referer: {referer}", f"User-Agent: {UA}"]}
    if filename: opts["out"] = filename
    if out_dir:  opts["dir"] = out_dir
    payload = json.dumps({
        "jsonrpc": "2.0", "id": str(uuid.uuid4()),
        "method": "aria2.addUri",
        "params": [f"token:{ARIA2_SECRET}", [dl_url], opts],
    }).encode()
    req = urllib.request.Request(ARIA2_RPC_URL, data=payload,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=10)
    return json.loads(resp.read().decode()).get("result", "")


def push_one(dl_url, cs, referer, filename, out_dir, quiet=False):
    try:
        gid = aria2_add(dl_url, cs, referer, filename, out_dir)
        if gid:
            if not quiet: print(f"      [✓] GID={gid}  {filename}")
            return True
    except urllib.error.URLError:
        if not quiet:
            print(f"      [✗] Motrix 未启动, RPC: {ARIA2_RPC_URL}")
    except Exception as e:
        if not quiet: print(f"      [✗] {e}")
    return False


def print_aria2c(dl_url, cs, referer, filename, out_dir):
    print(f'aria2c --header="Cookie: {cs}" \\')
    print(f'  --header="Referer: {referer}" \\')
    print(f'  --header="User-Agent: {UA}" \\')
    if filename: print(f'  -o "{filename}" \\')
    if out_dir:  print(f'  -d "{out_dir}" \\')
    print(f'  "{dl_url}"')


# ─── 主流程 ──────────────────────────────────────────────

def handle_file(tenant, token, jar, args):
    share_url = f"https://{tenant}.feishu.cn/file/{token}"
    dl_url = f"https://{tenant}.feishu.cn/space/api/box/stream/download/all/{token}"
    cs = cookie_string(jar)

    print(f"[2/3] 探测文件 ...")
    filename, size = probe_file(tenant, token, jar, share_url)
    print(f"      {filename}  ({human_size(size)})")

    if args.list: return
    if args.aria2c:
        print_aria2c(dl_url, cs, share_url, filename, args.dir); return

    print(f"[3/3] 推送到 Motrix ...")
    if not push_one(dl_url, cs, share_url, filename, args.dir):
        print(f"\n  降级输出 aria2c 命令:\n")
        print_aria2c(dl_url, cs, share_url, filename, args.dir)


def handle_folder(tenant, token, jar, args):
    base = f"https://{tenant}.feishu.cn"
    cs = cookie_string(jar)

    print(f"[2/4] 递归扫描文件夹 ...")
    all_files = walk_folder(tenant, token, jar)

    downloadable = [f for f in all_files if f["type"] == 12]
    skipped      = [f for f in all_files if f["type"] != 12]

    print(f"      共 {len(all_files)} 项: "
          f"{len(downloadable)} 可下载, {len(skipped)} 在线文档(跳过)")

    if skipped:
        print(f"\n  ⏭ 跳过的在线文档:")
        for f in skipped:
            print(f"    {f['type_name']}  {f['path']}")

    if not downloadable:
        print("\n  没有可下载的文件"); return

    # 探测真实文件名和大小
    print(f"\n[3/4] 探测文件信息 ...")
    total_size = 0
    for f in downloadable:
        referer = f.get("url", f"{base}/drive/folder/{token}")
        try:
            real_name, size = probe_file(tenant, f["obj_token"], jar, referer)
            f["real_name"] = real_name or f["name"]
            f["size"] = size or f["size"]
        except:
            f["real_name"] = f["name"]
        total_size += f["size"]

    # 打印文件列表
    print(f"\n  {'─'*60}")
    print(f"  {'#':>3}  {'文件名':<35} {'大小':>10}  路径")
    print(f"  {'─'*60}")
    for i, f in enumerate(downloadable):
        sz = human_size(f["size"]) if f["size"] else "  ?"
        print(f"  {i+1:>3}  {f['real_name']:<35} {sz:>10}  {f['path']}")
    print(f"  {'─'*60}")
    print(f"  合计: {len(downloadable)} 文件, {human_size(total_size)}")

    if args.list: return

    # 下载
    print(f"\n[4/4] {'aria2c 命令' if args.aria2c else '推送 Motrix'} ...")
    ok = 0
    for f in downloadable:
        dl_url = f"{base}/space/api/box/stream/download/all/{f['obj_token']}"
        sub = os.path.dirname(f["path"])
        out_dir = os.path.join(args.dir, sub) if args.dir and sub else (args.dir or None)

        if args.aria2c:
            print_aria2c(dl_url, cs, base, f["real_name"], out_dir)
            print()
            ok += 1
        else:
            if push_one(dl_url, cs, base, f["real_name"], out_dir, quiet=True):
                ok += 1
                print(f"      [✓] {f['real_name']}")
            else:
                print(f"      [✗] {f['real_name']} — Motrix 未响应")
                print(f"          降级 aria2c:\n")
                print_aria2c(dl_url, cs, base, f["real_name"], out_dir)
                return  # Motrix 挂了就不继续了

    print(f"\n      完成! {ok}/{len(downloadable)}")


# ─── 入口 ────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description="飞书分享解析下载器 v2")
    ap.add_argument("url", help="飞书分享链接 (文件/文件夹)")
    ap.add_argument("-d", "--dir", default=None, help="下载目录")
    ap.add_argument("--list", action="store_true", help="仅列出,不下载")
    ap.add_argument("--aria2c", action="store_true", help="输出 aria2c 命令")
    args = ap.parse_args()

    tenant, token, lt = parse_share_url(args.url)
    if not token:
        print(f"[✗] 不支持的链接: {args.url}")
        print(f"    格式: https://xxx.feishu.cn/file/xxxToken")
        print(f"          https://xxx.feishu.cn/drive/folder/xxxToken")
        sys.exit(1)

    print(f"\n{'📄' if lt=='file' else '📁'} 飞书分享解析  [{lt}]  {tenant}/{token}")

    print(f"[1/{3 if lt=='file' else 4}] 获取匿名会话 ...")
    jar = fetch_session(args.url)
    print(f"      {sum(1 for _ in jar)} cookies")

    if lt == "file":
        handle_file(tenant, token, jar, args)
    else:
        handle_folder(tenant, token, jar, args)
    print()


if __name__ == "__main__":
    main()