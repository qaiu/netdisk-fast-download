const { test } = require('node:test')
const assert = require('node:assert/strict')
const { buildParseFileMeta, formatByteSize, panDisplayName } = require('./parseResultMeta.js')

test('formats binary size like 169.1 M', () => {
  assert.equal(formatByteSize(0), '0 B')
  assert.equal(formatByteSize(169.1 * 1024 * 1024), '169.1 M')
  assert.equal(formatByteSize(10 * 1024 * 1024), '10.0 M')
})

test('builds filename, size and pan tag', () => {
  assert.deepEqual(buildParseFileMeta({
    fileName: '末世冰潮.apk',
    sizeStr: '169.1 M',
    panType: 'lz',
    panList: [{ type: 'lz', name: '蓝奏云' }]
  }), ['末世冰潮.apk', '169.1 M', '蓝奏云'])
})

test('falls back to quark and uc names', () => {
  assert.equal(panDisplayName('qk', []), '夸克网盘')
  assert.equal(panDisplayName('UC', []), 'UC网盘')
  assert.deepEqual(buildParseFileMeta({
    fileName: 'bilitools_1.4.7_amd64.AppImage',
    size: 92899832,
    panType: 'qk'
  })[2], '夸克网盘')
})

test('omits missing fields', () => {
  assert.deepEqual(buildParseFileMeta({}), [])
  assert.deepEqual(buildParseFileMeta({ fileName: 'a.txt' }), ['a.txt'])
})
