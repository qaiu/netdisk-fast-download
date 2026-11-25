/**
 * 目录文件名筛选与批量勾选范围。
 *
 * 只处理已经加载的行，不发起下一页请求。分页仍由 folderPaging 的
 * nextCursor / pageCursor 推进；这里只决定新页里哪些行可见、哪些会被全选。
 */
const { fileIdentity, uniqueFiles } = require('./batchTreeCollect')
const { appendUnique, nextState } = require('./folderPaging')

function normalizeQuery(keyword) {
  return String(keyword == null ? '' : keyword).trim().toLowerCase()
}

function matchesFileName(file, keyword) {
  const query = normalizeQuery(keyword)
  if (!query) return true
  return String((file && file.fileName) || '').toLowerCase().includes(query)
}

/** 空关键字返回副本，不清空调用方持有的已加载数组。 */
function filterByFileName(list, keyword) {
  const rows = list || []
  const query = normalizeQuery(keyword)
  if (!query) return rows.slice()
  return rows.filter(file => matchesFileName(file, keyword))
}

function isSelectableFile(file) {
  return !!(
    file
    && file.parserUrl
    && file.fileType !== 'folder'
    && file.fileType !== 'url'
    && file.fileType !== 'more'
  )
}

function countableEntries(list) {
  return (list || []).filter(file => file && file.fileType !== 'more' && file.id !== 'root')
}

function filterSummary(loaded, keyword) {
  const rows = countableEntries(loaded)
  const matched = filterByFileName(rows, keyword)
  return {
    loaded: rows.length,
    matched: matched.length,
    text: `仅筛选已加载的 ${rows.length} 项，匹配 ${matched.length} 项`
  }
}

/**
 * 全选：只并入当前关键字命中的已加载可选文件。
 * 不在这份可见集合里的原选择保留，隐藏项不会被加进来。
 */
function selectMatching(selected, loaded, keyword, isSelectable = isSelectableFile) {
  const visible = filterByFileName(loaded, keyword).filter(file => isSelectable(file))
  const visibleKeys = new Set(visible.map(fileIdentity))
  const kept = (selected || []).filter(file => !visibleKeys.has(fileIdentity(file)))
  return uniqueFiles(kept.concat(visible))
}

/** 取消全选：只移出当前可见可选文件，范围外的选择不动。 */
function deselectMatching(selected, loaded, keyword, isSelectable = isSelectableFile) {
  const visibleKeys = new Set(
    filterByFileName(loaded, keyword)
      .filter(file => isSelectable(file))
      .map(fileIdentity)
  )
  return (selected || []).filter(file => !visibleKeys.has(fileIdentity(file)))
}

function toggleSelected(selected, file, isSelectable = isSelectableFile) {
  const list = (selected || []).slice()
  if (!isSelectable(file)) return list
  const key = fileIdentity(file)
  const index = list.findIndex(item => fileIdentity(item) === key)
  if (index >= 0) {
    list.splice(index, 1)
    return list
  }
  list.push(file)
  return list
}

function isSelected(selected, file) {
  const key = fileIdentity(file)
  return (selected || []).some(item => fileIdentity(item) === key)
}

/**
 * 已加载树节点。未展开的文件夹不往下走，因此不会把未请求的子项算进全选范围。
 * 「加载更多」占位节点跳过。
 */
function collectLoadedNodes(node) {
  const out = []
  for (const child of (node && node.childNodes) || []) {
    const data = child && child.data
    if (!data || data.fileType === 'more') continue
    out.push(data)
    if (child.loaded) {
      out.push(...collectLoadedNodes(child))
    }
  }
  return out
}

/**
 * 用目录分页的 nextCursor 追加一页。
 * 已有选择原样保留：新页即使命中关键字也不会被静默勾上。
 */
function applyCursorPage(state, page) {
  const loaded = appendUnique(state.loaded, (page && page.list) || [])
  const listUrl = (state.more && state.more.listUrl) || state.listUrl
  return {
    keyword: state.keyword,
    view: state.view,
    loaded,
    selected: (state.selected || []).slice(),
    listUrl,
    more: nextState(listUrl, page)
  }
}

function switchView(state, view) {
  return {
    ...state,
    view,
    keyword: state.keyword,
    selected: (state.selected || []).slice()
  }
}

function clearKeyword(state) {
  return {
    ...state,
    keyword: ''
  }
}

module.exports = {
  normalizeQuery,
  matchesFileName,
  filterByFileName,
  isSelectableFile,
  filterSummary,
  selectMatching,
  deselectMatching,
  toggleSelected,
  isSelected,
  collectLoadedNodes,
  applyCursorPage,
  switchView,
  clearKeyword
}
