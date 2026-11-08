/**
 * 证件照处理：居中裁剪为指定比例并压缩为 JPEG data URI。
 * 默认一寸照 3:4（300x400），导出 PDF/Word 时直接使用。
 */
export function cropAndCompressPhoto(file, { width = 300, height = 400, quality = 0.85 } = {}) {
  return new Promise((resolve, reject) => {
    if (!file || !file.type || !file.type.startsWith('image/')) {
      reject(new Error('not-image'))
      return
    }
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      try {
        const ratio = width / height
        let sx = 0
        let sy = 0
        let sw = img.naturalWidth
        let sh = img.naturalHeight
        if (sw / sh > ratio) {
          sw = sh * ratio
          sx = (img.naturalWidth - sw) / 2
        } else {
          sh = sw / ratio
          sy = (img.naturalHeight - sh) / 2
        }
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        canvas.getContext('2d').drawImage(img, sx, sy, sw, sh, 0, 0, width, height)
        URL.revokeObjectURL(url)
        resolve(canvas.toDataURL('image/jpeg', quality))
      } catch (err) {
        URL.revokeObjectURL(url)
        reject(err)
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('load-error'))
    }
    img.src = url
  })
}
