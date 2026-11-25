const { describe, it } = require('node:test')
const assert = require('node:assert/strict')

const {
  toPageUrl,
  readPage,
  appendUnique,
  nextState,
  makeMoreNode,
  isMoreNode
} = require('./folderPaging.js')

function file(id) {
  return { fileId: id, fileName: `${id}.apk`, fileType: 'file', parserUrl: `/v2/redirectUrl/lz/${id}` }
}

describe('toPageUrl', () => {
  it('switches getFileList to getFileListPage and keeps existing params', () => {
    assert.equal(
      toPageUrl('http://h/v2/getFileList?url=https%3A%2F%2Fa.com%2Fb1&dirId=7&auth=x%2By', 2, 'ab-_c='),
      'http://h/v2/getFileListPage?url=https%3A%2F%2Fa.com%2Fb1&dirId=7&auth=x%2By&page=2&pageCursor=ab-_c%3D'
    )
  })

  it('works without query and without cursor', () => {
    assert.equal(toPageUrl('/v2/getFileList', 1), '/v2/getFileListPage?page=1')
  })

  it('does not page unknown folder links', () => {
    assert.equal(toPageUrl('https://other/api/list?x=1', 2), null)
    assert.equal(toPageUrl('/v2/getFileListPage?url=a', 2), null)
    assert.equal(toPageUrl('', 2), null)
  })
})

describe('readPage', () => {
  it('reads paged envelope', () => {
    const page = readPage({ list: [file('a')], paged: true, page: 1, hasMore: true, nextPage: 2, nextCursor: 'c' })
    assert.equal(page.list.length, 1)
    assert.equal(page.hasMore, true)
    assert.equal(page.nextPage, 2)
    assert.equal(page.nextCursor, 'c')
  })

  it('treats legacy array as complete list', () => {
    const page = readPage([file('a'), file('b')])
    assert.equal(page.list.length, 2)
    assert.equal(page.hasMore, false)
    assert.equal(nextState('/v2/getFileList', page), null)
  })

  it('hasMore without a valid nextPage is not followed', () => {
    assert.equal(readPage({ list: [], hasMore: true, nextPage: null }).hasMore, false)
    assert.equal(readPage(null).list.length, 0)
  })
})

describe('appendUnique', () => {
  it('keeps loaded items and drops duplicates from the next page', () => {
    const merged = appendUnique([file('a'), file('b')], [file('b'), file('c')])
    assert.deepEqual(merged.map(f => f.fileId), ['a', 'b', 'c'])
  })

  it('a failed (empty) load leaves loaded items untouched', () => {
    const loaded = [file('a')]
    assert.deepEqual(appendUnique(loaded, undefined), loaded)
  })
})

describe('more node', () => {
  it('carries the next page state and is not a file or folder', () => {
    const state = nextState('/v2/getFileList?url=x', readPage({ list: [], hasMore: true, nextPage: 3, nextCursor: 'k' }))
    const node = makeMoreNode('fid:1', state)
    assert.equal(isMoreNode(node), true)
    assert.equal(node.isLeaf, true)
    assert.equal(node.paging.nextPage, 3)
    assert.equal(node.paging.nextCursor, 'k')
    assert.equal(isMoreNode(file('a')), false)
  })
})
