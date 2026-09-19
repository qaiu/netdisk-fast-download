const { describe, it } = require('node:test')
const assert = require('node:assert/strict')

const {
  DEFAULT_BATCH_MAX_DEPTH,
  getTreeNodeId,
  normalizeTreeItem,
  fileIdentity,
  uniqueFiles,
  collectFolderFiles
} = require('./batchTreeCollect.js')

function file(name, extra = {}) {
  return {
    fileName: name,
    fileType: 'file',
    parserUrl: `/v2/redirectUrl/demo/${name}`,
    ...extra
  }
}

function folder(name, extra = {}) {
  return {
    fileName: name,
    fileType: 'folder',
    parserUrl: `/v2/getFileList?dir=${name}`,
    ...extra
  }
}

function makeNode(data, children = [], { loaded = true, expanded = true, failLoad = false } = {}) {
  const node = {
    data,
    loaded,
    expanded,
    childNodes: [],
    expand(cb) {
      if (failLoad) throw new Error('load failed')
      this.loaded = true
      this.expanded = true
      if (cb) cb()
    }
  }
  node.childNodes = children.map((child) => (child.data ? child : makeNode(child)))
  return node
}

function createStore(rootNode) {
  const map = new Map()
  const walk = (node) => {
    map.set(getTreeNodeId(node.data), node)
    for (const child of node.childNodes || []) walk(child)
  }
  walk(rootNode)
  return {
    getNode(data) {
      return map.get(getTreeNodeId(data)) || null
    },
    async ensureLoaded(node) {
      if (node.loaded && node.expanded) return node
      await new Promise((resolve) => node.expand(resolve))
      for (const child of node.childNodes || []) {
        map.set(getTreeNodeId(child.data), child)
      }
      return node
    },
    isDownloadable(data) {
      return !!(data && data.parserUrl && data.fileType !== 'folder' && data.fileType !== 'url')
    }
  }
}

describe('batchTreeCollect helpers', () => {
  it('uses checked folder as depth 0 and defaults max depth to 5', () => {
    assert.equal(DEFAULT_BATCH_MAX_DEPTH, 5)
    assert.equal(getTreeNodeId({ id: 'root' }), 'root')
    assert.equal(normalizeTreeItem({ fileId: 'abc', fileType: 'folder' }).id, 'fid:abc')
    assert.equal(normalizeTreeItem({ fileId: 'abc', fileType: 'folder' }).isLeaf, false)
  })

  it('deduplicates files by parserUrl + name + id', () => {
    const a = file('a.txt', { fileId: '1' })
    const dup = file('a.txt', { fileId: '1' })
    const b = file('b.txt', { fileId: '2' })
    assert.equal(fileIdentity(a), fileIdentity(dup))
    assert.deepEqual(uniqueFiles([a, dup, b]).map((f) => f.fileName), ['a.txt', 'b.txt'])
  })

  it('collects current-level files and recurses into folders', async () => {
    const leafA = file('a.txt')
    const leafB = file('b.txt')
    const nested = folder('nested')
    const root = folder('root')
    const store = createStore(makeNode(root, [
      makeNode(leafA),
      makeNode(nested, [makeNode(leafB)])
    ]))

    const ctx = await collectFolderFiles({
      folderData: root,
      maxDepth: 4,
      ...store
    })
    assert.equal(ctx.depthExceeded, false)
    assert.deepEqual(ctx.files.map((f) => f.fileName).sort(), ['a.txt', 'b.txt'])
  })

  it('default depth 5 expands four descendant folders then stops', async () => {
    // depth 0 root → 1 d1 → 2 d2 → 3 d3 → 4 d4 → 5 d5 (cap, still has leftover folder)
    const skipped = file('skipped.txt')
    const capFile = file('cap.txt')
    const d5 = folder('d5')
    const d4 = folder('d4')
    const d3 = folder('d3')
    const d2 = folder('d2')
    const d1 = folder('d1')
    const root = folder('root')
    const tree = makeNode(root, [
      makeNode(d1, [
        makeNode(d2, [
          makeNode(d3, [
            makeNode(d4, [
              makeNode(d5, [
                makeNode(capFile),
                makeNode(folder('d6'), [makeNode(skipped)], { loaded: false, expanded: false })
              ], { loaded: true })
            ])
          ])
        ])
      ])
    ])
    const store = createStore(tree)
    const loadedIds = []
    const ctx = await collectFolderFiles({
      folderData: root,
      maxDepth: DEFAULT_BATCH_MAX_DEPTH,
      getNode: store.getNode,
      ensureLoaded: async (node) => {
        loadedIds.push(node.data.fileName)
        return store.ensureLoaded(node)
      },
      isDownloadable: store.isDownloadable
    })
    assert.equal(ctx.depthExceeded, true)
    assert.deepEqual(ctx.files.map((f) => f.fileName), ['cap.txt'])
    assert.ok(!ctx.files.some((f) => f.fileName === 'skipped.txt'))
    assert.ok(loadedIds.includes('d4'))
    assert.ok(!loadedIds.includes('d5'))
  })

  it('stops expanding at maxDepth and warns when remaining folders exist', async () => {
    // depth 0: root
    // depth 1: d1
    // depth 2: d2
    // depth 3: d3
    // depth 4: d4 (cap when maxDepth=4) — still has child folder d5
    const deepFile = file('deep.txt')
    const skipped = file('skipped.txt')
    const d5 = folder('d5')
    const d4 = folder('d4')
    const d3 = folder('d3')
    const d2 = folder('d2')
    const d1 = folder('d1')
    const root = folder('root')

    const d5Node = makeNode(d5, [makeNode(skipped)], { loaded: false, expanded: false })
    const d4Node = makeNode(d4, [makeNode(deepFile), d5Node], { loaded: true })
    const tree = makeNode(root, [
      makeNode(d1, [
        makeNode(d2, [
          makeNode(d3, [d4Node])
        ])
      ])
    ])
    const store = createStore(tree)

    let loadedIds = []
    const ctx = await collectFolderFiles({
      folderData: root,
      maxDepth: 4,
      getNode: store.getNode,
      ensureLoaded: async (node) => {
        loadedIds.push(node.data.fileName)
        return store.ensureLoaded(node)
      },
      isDownloadable: store.isDownloadable
    })

    assert.equal(ctx.depthExceeded, true)
    assert.deepEqual(ctx.files.map((f) => f.fileName), ['deep.txt'])
    assert.ok(!ctx.files.some((f) => f.fileName === 'skipped.txt'))
    assert.ok(!loadedIds.includes('d4'), 'depth-4 folder must not be expanded')
    assert.ok(loadedIds.includes('root'))
    assert.ok(loadedIds.includes('d3'))
  })

  it('collects already-loaded files at the depth cap without expanding further', async () => {
    const capFile = file('cap.txt')
    const inner = folder('inner')
    const cap = folder('cap')
    const root = folder('root')
    const tree = makeNode(root, [
      makeNode(cap, [
        makeNode(capFile),
        makeNode(inner, [makeNode(file('too-deep.txt'))], { loaded: false })
      ])
    ])
    const store = createStore(tree)
    const ctx = await collectFolderFiles({
      folderData: root,
      maxDepth: 1,
      ...store
    })
    assert.equal(ctx.depthExceeded, true)
    assert.deepEqual(ctx.files.map((f) => f.fileName), ['cap.txt'])
  })

  it('skips a branch when ensureLoaded throws', async () => {
    const ok = file('ok.txt')
    const badFolder = folder('bad')
    const root = folder('root')
    const badNode = makeNode(badFolder, [makeNode(file('lost.txt'))], { loaded: false, failLoad: true })
    const tree = makeNode(root, [makeNode(ok), badNode])
    const store = createStore(tree)
    const ctx = await collectFolderFiles({
      folderData: root,
      maxDepth: 4,
      getNode: store.getNode,
      ensureLoaded: async (node) => {
        if (node.data.fileName === 'bad') throw new Error('network')
        return store.ensureLoaded(node)
      },
      isDownloadable: store.isDownloadable
    })
    assert.deepEqual(ctx.files.map((f) => f.fileName), ['ok.txt'])
  })
})
