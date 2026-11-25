/**
 * 目录分页（/v2/getFileListPage）前端辅助。
 *
 * - 后端给出的目录链接是 /v2/getFileList?...，换成 /v2/getFileListPage 并带 page / pageCursor 即可分页。
 * - 不是 getFileList 链接的目录（例如第三方解析器返回的其它地址）不分页，按原样整体加载。
 * - 加载失败（如蓝奏「请刷新，重试」限流）只提示重试，已加载的页保留不动。
 */
const { fileIdentity } = require('./batchTreeCollect.js')

const MORE_TYPE = 'more'
const LIST_PATH_RE = /^(.*?\/v2\/getFileList)(\?.*)?$/

function toPageUrl(listUrl, page = 1, cursor = '') {
  if (!listUrl) return null
  const m = String(listUrl).match(LIST_PATH_RE)
  if (!m) return null
  let url = `${m[1]}Page${m[2] || ''}`
  const extra = [`page=${encodeURIComponent(page)}`]
  if (cursor) extra.push(`pageCursor=${encodeURIComponent(cursor)}`)
  url += (url.includes('?') ? '&' : '?') + extra.join('&')
  return url
}

/** 兼容两种响应：分页 envelope 或旧接口的纯数组。 */
function readPage(data) {
  if (Array.isArray(data)) {
    return { list: data, paged: false, hasMore: false, nextPage: null, nextCursor: null }
  }
  const d = data || {}
  const hasMore = !!d.hasMore && Number.isInteger(d.nextPage)
  return {
    list: Array.isArray(d.list) ? d.list : [],
    paged: !!d.paged,
    hasMore,
    nextPage: hasMore ? d.nextPage : null,
    nextCursor: hasMore ? (d.nextCursor || null) : null,
    total: d.total ?? null
  }
}

/** 追加下一页，按文件身份去重，保持已加载项的顺序。 */
function appendUnique(existing, incoming) {
  const out = [...(existing || [])]
  const seen = new Set(out.map(fileIdentity))
  for (const item of incoming || []) {
    const key = fileIdentity(item)
    if (seen.has(key)) continue
    seen.add(key)
    out.push(item)
  }
  return out
}

/** 下一页的请求状态；没有更多时返回 null。 */
function nextState(listUrl, page) {
  if (!listUrl || !page || !page.hasMore) return null
  return { listUrl, nextPage: page.nextPage, nextCursor: page.nextCursor, loading: false, error: '' }
}

function makeMoreNode(parentId, state) {
  return {
    id: `${parentId}::more`,
    fileName: '加载更多…',
    fileType: MORE_TYPE,
    isLeaf: true,
    disabled: true,
    paging: state
  }
}

function isMoreNode(data) {
  return !!(data && data.fileType === MORE_TYPE)
}

module.exports = {
  MORE_TYPE,
  toPageUrl,
  readPage,
  appendUnique,
  nextState,
  makeMoreNode,
  isMoreNode
}
