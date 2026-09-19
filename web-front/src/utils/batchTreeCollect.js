/**
 * 文件树批量勾选：按深度递归展开并收集可下载文件。
 *
 * Depth convention (checked folder = depth 0):
 * - 用户勾选的文件夹为第 0 层。
 * - 其直接子项为第 1 层。
 * - 当 depth < maxDepth 时继续展开子文件夹；默认 maxDepth = 5，
 *   即最多再向下展开 5 层（第 0/1/2/3/4 层会 load，第 5 层文件夹不再展开）。
 * - 触及上限时若仍有未加载/剩余子文件夹，标记 depthExceeded，由 UI 提示跳过。
 */

const DEFAULT_BATCH_MAX_DEPTH = 5

function getTreeNodeId(item) {
  if (!item) return ''
  if (item.id) return String(item.id)
  if (item.fileId) return `fid:${item.fileId}`
  if (item.parserUrl) return `url:${item.parserUrl}`
  return `name:${item.fileName || ''}:${item.fileType || ''}`
}

function normalizeTreeItem(item) {
  if (!item || typeof item !== 'object') return item
  return {
    ...item,
    id: getTreeNodeId(item),
    isLeaf: item.fileType !== 'folder'
  }
}

function fileIdentity(file) {
  return `${file?.parserUrl || ''}::${file?.fileName || ''}::${file?.fileId || file?.id || ''}`
}

function uniqueFiles(files) {
  const seen = new Set()
  const out = []
  for (const file of files || []) {
    const key = fileIdentity(file)
    if (!key || seen.has(key)) continue
    seen.add(key)
    out.push(file)
  }
  return out
}

function isFolderNode(data) {
  return !!(data && data.fileType === 'folder')
}

function collectAlreadyLoadedFiles(node, isDownloadable, ctx) {
  for (const child of node.childNodes || []) {
    if (isDownloadable(child.data)) {
      ctx.files.push(child.data)
    } else if (isFolderNode(child.data) && child.loaded) {
      collectAlreadyLoadedFiles(child, isDownloadable, ctx)
    }
  }
}

function hasUnexploredFolders(node) {
  if (!node) return false
  if (isFolderNode(node.data) && !node.loaded) return true
  return (node.childNodes || []).some((child) => {
    if (!isFolderNode(child.data)) return false
    if (!child.loaded) return true
    return hasUnexploredFolders(child)
  })
}

/**
 * 递归展开文件夹并收集可下载文件（对接 el-tree node / loadNode）。
 *
 * @param {object} opts
 * @param {object} opts.folderData 勾选的文件夹 data（depth 从 0 计）
 * @param {number} [opts.depth=0]
 * @param {number} opts.maxDepth
 * @param {(data: object) => any} opts.getNode el-tree getNode
 * @param {(node: any) => Promise<any>} opts.ensureLoaded 展开并触发懒加载
 * @param {(data: object) => boolean} opts.isDownloadable
 * @param {Set<string>} [opts.visitedKeys]
 * @param {(data: object) => string} [opts.getKey]
 * @param {{ files: object[], depthExceeded: boolean }} [opts.ctx]
 */
async function collectFolderFiles(opts) {
  const {
    folderData,
    depth = 0,
    maxDepth,
    getNode,
    ensureLoaded,
    isDownloadable,
    getKey = getTreeNodeId,
    visitedKeys = new Set(),
    ctx = { files: [], depthExceeded: false }
  } = opts

  if (!folderData || typeof maxDepth !== 'number') return ctx

  const key = getKey(folderData)
  if (key) visitedKeys.add(key)

  const node = getNode(folderData)
  if (!node) return ctx

  if (depth >= maxDepth) {
    // 未 load 的节点在真实 el-tree 中没有 childNodes，不能把预置子项算进去
    if (node.loaded) {
      collectAlreadyLoadedFiles(node, isDownloadable, ctx)
      if (hasUnexploredFolders(node)) {
        ctx.depthExceeded = true
      }
    } else if (isFolderNode(node.data)) {
      ctx.depthExceeded = true
    }
    return ctx
  }

  try {
    await ensureLoaded(node)
  } catch {
    // 单层加载失败：跳过该分支，不中断整次收集
    return ctx
  }

  for (const child of node.childNodes || []) {
    const data = child.data
    if (!data) continue
    if (isDownloadable(data)) {
      ctx.files.push(data)
    } else if (isFolderNode(data)) {
      await collectFolderFiles({
        ...opts,
        folderData: data,
        depth: depth + 1,
        visitedKeys,
        ctx
      })
    }
  }
  return ctx
}

module.exports = {
  DEFAULT_BATCH_MAX_DEPTH,
  getTreeNodeId,
  normalizeTreeItem,
  fileIdentity,
  uniqueFiles,
  isFolderNode,
  collectFolderFiles
}
