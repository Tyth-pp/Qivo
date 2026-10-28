import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validImageAddress } from './image.ts'
test('图片地址允许上传路径和 HTTP 链接，拒绝磁盘路径与脚本协议', () => {
  for (const v of ['', '/upload/2026/10/23/a.jpg', 'https://example.com/a.jpg']) assert.equal(validImageAddress(v), true)
  for (const v of ['D:/mall-upload/a.jpg', 'D:\\mall-upload\\a.jpg', 'file:///D:/a.jpg', 'javascript:alert(1)', '//example.com/a.jpg']) assert.equal(validImageAddress(v), false)
})
