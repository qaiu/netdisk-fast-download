const PAN_NAMES = {
  qk: '夸克网盘',
  uc: 'UC网盘',
  lz: '蓝奏云',
  iz: '蓝奏云优享',
  fj: '小飞机盘',
  ye: '123云盘',
  ec: '移动云空间',
  le: '联想乐云',
  qqsc: 'QQ闪传',
  pic: 'iCloud'
}

/** 与专业版摘要一致：169.1 M，而不是 169.1MB。 */
function formatByteSize(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) return ''
  const units = ['B', 'K', 'M', 'G', 'T']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  const text = index === 0 ? String(Math.round(size)) : size.toFixed(1)
  return `${text} ${units[index]}`
}

function panDisplayName(panType, panList) {
  const type = String(panType || '').toLowerCase()
  if (!type) return ''
  const found = (panList || []).find(item => String(item.type || '').toLowerCase() === type)
  if (found && found.name) return found.name
  return PAN_NAMES[type] || type
}

/** 单文件解析成功摘要：文件名、大小、网盘类型。 */
function buildParseFileMeta({ fileName, size, sizeStr, panType, panList } = {}) {
  const meta = []
  if (fileName) meta.push(String(fileName))
  const sizeText = sizeStr || formatByteSize(size)
  if (sizeText) meta.push(sizeText)
  const pan = panDisplayName(panType, panList)
  if (pan) meta.push(pan)
  return meta
}

module.exports = {
  formatByteSize,
  panDisplayName,
  buildParseFileMeta
}
