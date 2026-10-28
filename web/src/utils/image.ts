// 表单只接受浏览器访问地址，不把电脑磁盘路径当作图片 URL。
export function validImageAddress(value: string): boolean {
  return !value || /^(https?:\/\/[^\s]+|\/(?!\/)[^\s\\]*)$/i.test(value)
}
