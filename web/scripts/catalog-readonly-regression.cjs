// 对已运行的本机商城做只读检查；阻止所有非 GET API 请求。
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require(process.env.QIVO_PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const root = path.resolve(__dirname, '../..');
  const out = path.join(root, 'docs/设计原型/商品目录扩充验收');
  await fs.mkdir(out, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const errors = [], writes = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/**', route => {
    if (route.request().method() !== 'GET') { writes.push(route.request().method() + ' ' + route.request().url()); return route.abort(); }
    return route.continue();
  });
  const base = process.env.QIVO_LIVE_READ_ONLY_URL || 'http://localhost:5173';
  const checks = [];
  try {
    await page.goto(base + '/');
    await page.waitForFunction(() => document.querySelectorAll('.home-catalog .product-card').length === 12);
    assert.equal(await page.locator('.category-links > a').count(), 2);
    assert.equal(await page.locator('.home-products .el-button:disabled').count(), 0);
    assert.equal(await page.getByRole('group', { name: '商品子类', exact: true }).getByRole('button').count(), 5);
    for (const width of [1280, 1440, 1920]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.evaluate(() => document.fonts.ready);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await page.screenshot({ path: path.join(out, '首页-' + width + '.png'), fullPage: true });
    }
    checks.push('三种桌面宽度：首页两张大类卡片、两级分类及商品网格正常，无横向溢出');
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole('group', { name: '商品子类', exact: true }).getByRole('button', { name: '杯具', exact: true }).click();
    await page.waitForFunction(() => document.querySelectorAll('.home-catalog .product-card').length === 7);
    assert.equal(await page.getByRole('group', { name: '商品子类', exact: true }).getByRole('button').count(), 3);
    await page.screenshot({ path: path.join(out, '首页-杯具筛选.png'), fullPage: true });
    await page.getByRole('group', { name: '商品大类', exact: true }).getByRole('button', { name: '数码办公', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('.home-catalog .catalog-count')?.textContent.includes('14'));
    assert.equal(await page.getByRole('group', { name: '商品子类', exact: true }).getByRole('button').count(), 2);
    checks.push('首页大类与子类切换使用真实服务端分页：杯具7件，数码办公14件');
    await page.goto(base + '/product/list');
    await page.waitForFunction(() => document.querySelectorAll('.product-grid .product-card').length === 12);
    assert((await page.locator('.catalog-count').innerText()).includes('35'));
    const firstPage = await page.locator('.product-card h3').allTextContents();
    await page.locator('.el-pagination .btn-next').click();
    await page.waitForFunction(() => document.querySelector('.el-pager li.is-active')?.textContent.trim() === '2');
    await page.waitForTimeout(200);
    assert.notDeepEqual(await page.locator('.product-card h3').allTextContents(), firstPage);
    await page.locator('.el-pagination .btn-next').click();
    await page.waitForFunction(() => document.querySelectorAll('.product-grid .product-card').length === 11);
    await page.screenshot({ path: path.join(out, '商品列表-第三页.png'), fullPage: true });
    await page.getByRole('group', { name: '商品子类', exact: true }).getByRole('button', { name: '照明', exact: true }).click();
    await page.waitForFunction(() => document.querySelectorAll('.product-grid .product-card').length === 7);
    assert.equal(await page.locator('.el-pagination').count(), 0);
    assert((await page.locator('.store-nav a.active').allTextContents()).includes('居家生活'));
    checks.push('商品列表35件分三页；第三页筛选子类后回到第一页，导航高亮所属大类');
    const catalogResponse = await page.request.get(base + '/api/fore/product/list?page=1&pageSize=100');
    const catalog = (await catalogResponse.json()).data.records;
    const mug = catalog.find(p => p.name === '磨砂陶瓷马克杯');
    assert(mug);
    await page.goto(base + '/product/' + mug.id);
    await page.getByRole('heading', { name: mug.name, exact: true }).waitFor();
    assert(await page.getByRole('button', { name: '立即购买', exact: true }).isDisabled());
    await page.getByText('400 毫升', { exact: true }).click();
    assert(await page.getByRole('button', { name: '立即购买', exact: true }).isEnabled());
    assert((await page.locator('.detail-price').innerText()).includes('59.00'));
    await page.screenshot({ path: path.join(out, '商品详情-多规格.png'), fullPage: true });
    const soldOut = catalog.find(p => p.name === '便携固态移动硬盘');
    await page.goto(base + '/product/' + soldOut.id);
    await page.getByRole('heading', { name: soldOut.name, exact: true }).waitFor();
    await page.getByText('1 TB', { exact: true }).click();
    assert(await page.getByRole('button', { name: '立即购买', exact: true }).isDisabled());
    await page.getByText('该规格暂时缺货，请选择其他规格。', { exact: true }).waitFor();
    assert(!/演示|开发环境|示例商品/.test(await page.locator('body').innerText()));
    checks.push('多规格选择后价格与可购买状态更新；缺货规格禁止购买；未执行加购或下单');
    assert.deepEqual(errors, []);
    assert.deepEqual(writes, []);
    await fs.writeFile(path.join(out, '只读页面验证.json'), JSON.stringify({ date: new Date().toISOString(), checks, errors, writes }, null, 2));
    console.log(JSON.stringify({ groups: checks.length, pageErrors: errors.length, businessWrites: writes.length }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
