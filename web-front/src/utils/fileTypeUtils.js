const fileTypeUtils = {
  getFileExtension(filename) {
    if (!filename) return ''
    return filename.split('.').pop()
  },
  getFileTypeClass(file) {
    if (file.fileType === 'folder') return 'folder'
    const ext = this.getFileExtension(file.fileName)
    const fileTypes = {
      'image': ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'],
      'document': ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'rtf'],
      'archive': ['zip', 'rar', '7z', 'tar', 'gz'],
      'audio': ['mp3', 'wav', 'ogg', 'flac'],
      'video': ['mp4', 'avi', 'mov', 'wmv', 'mkv', 'flv'],
      'code': ['html', 'htm', 'css', 'js', 'json', 'php', 'py', 'java', 'c', 'cpp', 'h', 'sh', 'bat', 'md']
    }
    for (const [type, extensions] of Object.entries(fileTypes)) {
      if (extensions.includes(ext.toLowerCase())) {
        return type
      }
    }
    return 'document'
  },
  getFileIcon(file) {
    if (file.fileType === 'folder') return 'fas fa-folder'
    const ext = this.getFileExtension(file.fileName)
    const iconMap = {
      'jpg': 'fas fa-file-image', 'jpeg': 'fas fa-file-image', 'png': 'fas fa-file-image',
      'gif': 'fas fa-file-image', 'bmp': 'fas fa-file-image', 'svg': 'fas fa-file-image', 'webp': 'fas fa-file-image',
      'pdf': 'fas fa-file-pdf', 'doc': 'fas fa-file-word', 'docx': 'fas fa-file-word',
      'xls': 'fas fa-file-excel', 'xlsx': 'fas fa-file-excel', 'ppt': 'fas fa-file-powerpoint', 'pptx': 'fas fa-file-powerpoint',
      'txt': 'fas fa-file-alt', 'rtf': 'fas fa-file-alt',
      'zip': 'fas fa-file-archive', 'rar': 'fas fa-file-archive', '7z': 'fas fa-file-archive',
      'tar': 'fas fa-file-archive', 'gz': 'fas fa-file-archive',
      'mp3': 'fas fa-file-audio', 'wav': 'fas fa-file-audio', 'ogg': 'fas fa-file-audio', 'flac': 'fas fa-file-audio',
      'mp4': 'fas fa-file-video', 'avi': 'fas fa-file-video', 'mov': 'fas fa-file-video',
      'wmv': 'fas fa-file-video', 'mkv': 'fas fa-file-video', 'flv': 'fas fa-file-video',
      'html': 'fas fa-file-code', 'htm': 'fas fa-file-code', 'css': 'fas fa-file-code',
      'js': 'fas fa-file-code', 'json': 'fas fa-file-code', 'php': 'fas fa-file-code',
      'py': 'fas fa-file-code', 'java': 'fas fa-file-code', 'c': 'fas fa-file-code',
      'cpp': 'fas fa-file-code', 'h': 'fas fa-file-code', 'sh': 'fas fa-file-code',
      'bat': 'fas fa-file-code', 'md': 'fas fa-file-code'
    }
    return iconMap[ext.toLowerCase()] || 'fas fa-file'
  },
  extractFileNameAndExt(url) {
    if (!url) return { name: '', ext: '' }
    const filenameParams = [
      'response-content-disposition', 'filename', 'filename*', 'fn', 'fname', 'download_name'
    ];
    let name = null;
    try {
      const u = new URL(url, window.location.origin);
      for (const param of filenameParams) {
        const value = u.searchParams.get(param);
        if (value) {
          if (param === 'response-content-disposition') {
            const match = value.match(/filename\*?=(.*'')?(?<FN>.*)/i);
            name = match && match.groups && match.groups['FN'] ? match.groups['FN'] : value;
          } else {
            name = value;
          }
          break;
        }
      }
      if (name) {
        name = decodeURIComponent(name).replace(/['"]/g, '');
      } else {
        const decodedUrl = decodeURIComponent(url);
        const paths = decodedUrl.split('/');
        name = paths[paths.length - 1].split('?')[0];
      }
      let ext = '';
      if (name) {
        const spl = name.split('.');
        ext = spl.length > 1 ? spl[spl.length - 1].toLowerCase() : '';
      }
      return { name, ext };
    } catch {
      return { name: '', ext: '' }
    }
  }
}

export default fileTypeUtils 