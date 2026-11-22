/**
 * 在用户点击里打开地址，并且不带 Referer。
 * 夸克 / UC 的公开文件 CDN 遇到站点 Referer 会返回 403 HTML。
 * 使用 referrerpolicy="no-referrer" 的链接，而不是只依赖 window.open 的特征字符串。
 */
export function openNoReferrer(url) {
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.target = '_blank'
  a.rel = 'noreferrer noopener'
  a.referrerPolicy = 'no-referrer'
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  a.remove()
}
