const { describe, it } = require('node:test')
const assert = require('node:assert/strict')

const { readPage } = require('./folderPaging')
const {
  normalizeQuery,
  filterByFileName,
  filterSummary,
  selectMatching,
  deselectMatching,
  toggleSelected,
  isSelected,
  collectLoadedNodes,
  applyCursorPage,
  switchView,
  clearKeyword
} = require('./nameFilter')

function file(name, id, extra = {}) {
  return {
    fileName: name,
    fileId: id,
    fileType: 'file',
    parserUrl: '/v2/redirectUrl/lz/same',
    ...extra
  }
}

function folder(name, id) {
  return {
    fileName: name,
    fileId: id,
    fileType: 'folder',
    parserUrl: `/v2/getFileList?dir=${id}`
  }
}

function baseState(overrides = {}) {
  return {
    keyword: '',
    view: 'pane',
    loaded: [],
    selected: [],
    listUrl: '/v2/getFileList?url=share',
    more: null,
    ...overrides
  }
}

describe('filename filter', () => {
  const loaded = [
    file('Alpha.TXT', '1'),
    file('notes.md', '2'),
    file('  photo.JPG', '3'),
    folder('Albums', '4')
  ]

  it('trims and matches a case-insensitive substring', () => {
    assert.equal(normalizeQuery('  ApK '), 'apk')
    const matched = filterByFileName(loaded, '  aL ')
    assert.deepEqual(matched.map(item => item.fileId), ['1', '4'])
  })

  it('blank keyword restores the loaded list without dropping rows', () => {
    const cleared = filterByFileName(loaded, '   ')
    assert.deepEqual(cleared.map(item => item.fileId), ['1', '2', '3', '4'])
    assert.notEqual(cleared, loaded)
  })

  it('describes loaded N and matched M', () => {
    const summary = filterSummary(loaded, 'photo')
    assert.equal(summary.loaded, 4)
    assert.equal(summary.matched, 1)
    assert.equal(summary.text, '仅筛选已加载的 4 项，匹配 1 项')
  })
})

describe('select and deselect scope', () => {
  const outside = file('kept.zip', 'out', { parserUrl: '/v2/redirectUrl/lz/out' })
  const match = file('Alpha.TXT', '1')
  const other = file('Beta.bin', '2')
  const hiddenFolder = folder('Alpha-dir', '3')
  const link = file('Alpha-link.url', '4', { fileType: 'url', parserUrl: '' })
  const loaded = [match, other, hiddenFolder, link]

  it('selects only loaded selectable files matching the keyword', () => {
    const selected = selectMatching([outside], loaded, 'alpha')
    assert.deepEqual(selected.map(item => item.fileId), ['out', '1'])
    assert.equal(isSelected(selected, other), false)
    assert.equal(isSelected(selected, hiddenFolder), false)
  })

  it('deselect removes only the current match and keeps selections outside it', () => {
    const selected = deselectMatching([outside, match, other], loaded, 'alpha')
    assert.deepEqual(selected.map(item => item.fileId), ['out', '2'])
  })

  it('does not select a loaded file hidden by the keyword', () => {
    const selected = selectMatching([other], loaded, 'alpha')
    assert.equal(isSelected(selected, other), true)
    assert.equal(isSelected(selected, match), true)
    const narrowed = selectMatching(selected, loaded, 'beta')
    assert.deepEqual(narrowed.map(item => item.fileId).sort(), ['1', '2'])
  })
})

describe('same name, different id', () => {
  const first = file('Report.TXT', 'a')
  const second = file('Report.TXT', 'b')

  it('keeps both files when selecting and can toggle one without the other', () => {
    const selected = selectMatching([], [first, second], 'report')
    assert.equal(selected.length, 2)
    assert.notEqual(selected[0], selected[1])
    const onlySecond = toggleSelected(selected, first)
    assert.deepEqual(onlySecond.map(item => item.fileId), ['b'])
    assert.equal(isSelected(onlySecond, first), false)
    assert.equal(isSelected(onlySecond, second), true)
  })

  it('distinguishes files that only differ by id when fileId is absent', () => {
    const left = { fileName: 'Report.TXT', id: 'x', fileType: 'file', parserUrl: '/v2/redirectUrl/lz/same' }
    const right = { fileName: 'Report.TXT', id: 'y', fileType: 'file', parserUrl: '/v2/redirectUrl/lz/same' }
    const selected = toggleSelected([], left)
    assert.equal(isSelected(selected, right), false)
    assert.equal(toggleSelected(selected, right).length, 2)
  })
})

describe('cursor page', () => {
  it('shows new matches from the next cursor without selecting them', () => {
    let state = baseState({
      keyword: 'alpha',
      loaded: [file('Alpha.TXT', '1'), file('Beta.bin', '2')],
      selected: [file('Beta.bin', '2')],
      more: { listUrl: '/v2/getFileList?url=share', nextPage: 2, nextCursor: 'c1', loading: false, error: '' }
    })
    assert.deepEqual(filterByFileName(state.loaded, state.keyword).map(item => item.fileId), ['1'])

    state = applyCursorPage(state, readPage({
      list: [file('ALPHA-extra.txt', '3'), file('Gamma.bin', '4')],
      paged: true,
      hasMore: true,
      nextPage: 3,
      nextCursor: 'c2'
    }))

    assert.deepEqual(state.loaded.map(item => item.fileId), ['1', '2', '3', '4'])
    assert.deepEqual(filterByFileName(state.loaded, state.keyword).map(item => item.fileId), ['1', '3'])
    assert.deepEqual(state.selected.map(item => item.fileId), ['2'])
    assert.equal(state.more.nextCursor, 'c2')
    assert.equal(state.more.nextPage, 3)
  })

  it('keeps load-more available when nothing matches yet', () => {
    let state = baseState({
      keyword: 'zzzz',
      loaded: [file('Alpha.TXT', '1')],
      more: { listUrl: '/v2/getFileList?url=share', nextPage: 2, nextCursor: 'c1', loading: false, error: '' }
    })
    assert.equal(filterByFileName(state.loaded, state.keyword).length, 0)
    assert.equal(state.more.nextCursor, 'c1')

    state = applyCursorPage(state, readPage({
      list: [file('zzzz-found.txt', '9')],
      hasMore: true,
      nextPage: 3,
      nextCursor: 'c2'
    }))
    assert.deepEqual(filterByFileName(state.loaded, state.keyword).map(item => item.fileId), ['9'])
    assert.equal(state.selected.length, 0)
    assert.ok(state.more)
  })
})

describe('view switch and clear', () => {
  it('keeps the keyword and selected identities when the view changes', () => {
    const chosen = file('Alpha.TXT', '1')
    const state = baseState({
      keyword: '  Alpha ',
      view: 'pane',
      loaded: [chosen, file('Beta.bin', '2')],
      selected: [chosen]
    })
    const tree = switchView(state, 'tree')
    assert.equal(tree.view, 'tree')
    assert.equal(tree.keyword, '  Alpha ')
    assert.deepEqual(tree.selected.map(item => item.fileId), ['1'])
    const back = switchView(tree, 'pane')
    assert.equal(back.keyword, state.keyword)
    assert.equal(isSelected(back.selected, chosen), true)
  })

  it('clearing the keyword shows every loaded row again', () => {
    const state = baseState({
      keyword: 'alpha',
      loaded: [file('Alpha.TXT', '1'), file('Beta.bin', '2')]
    })
    assert.deepEqual(filterByFileName(state.loaded, state.keyword).map(item => item.fileId), ['1'])
    const cleared = clearKeyword(state)
    assert.deepEqual(
      filterByFileName(cleared.loaded, cleared.keyword).map(item => item.fileId),
      ['1', '2']
    )
  })
})

describe('loaded tree nodes', () => {
  it('skips unloaded folders, hidden placeholders, and does not invent a fetch', () => {
    const secret = file('secret.txt', 'secret')
    const more = { data: { fileType: 'more', fileName: '加载更多…' }, loaded: true, childNodes: [] }
    const root = {
      childNodes: [
        { data: file('root.txt', 'root'), loaded: true, childNodes: [] },
        {
          data: folder('closed', 'closed'),
          loaded: false,
          childNodes: [{ data: secret, loaded: true, childNodes: [] }]
        },
        {
          data: folder('open', 'open'),
          loaded: true,
          childNodes: [
            { data: file('open-a.txt', 'oa'), loaded: true, childNodes: [] },
            more
          ]
        },
        more
      ]
    }
    const loaded = collectLoadedNodes(root)
    assert.deepEqual(loaded.map(item => item.fileId), ['root', 'closed', 'open', 'oa'])
    const selected = selectMatching([], loaded, '')
    assert.deepEqual(selected.map(item => item.fileId), ['root', 'oa'])
    assert.equal(isSelected(selected, secret), false)
  })
})
