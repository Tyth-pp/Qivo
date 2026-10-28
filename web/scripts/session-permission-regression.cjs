// 多标签会话与身份边界回归；独立构建、内存接口，不接触日常业务数据。
const { chromium } = require(process.env.QIVO_PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs/promises'), path = require('node:path'), http = require('node:http');
const assert = require('node:assert/strict');

async function main() {
  const root = path.resolve(__dirname, '../..');
  const out = path.join(root, process.env.QIVO_SESSION_OUT || 'docs/设计原型/会话权限巡检');
  await fs.mkdir(out, { recursive: true });
  const html = await fs.readFile(path.join(root, process.env.QIVO_UI_BUILD || 'web/dist', 'index.html'));
  const server = http.createServer((req, res) => { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const checks = [], errors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    context.setDefaultTimeout(10000);
    const base = `http://127.0.0.1:${server.address().port}`;
    let holdOldCart = false, rejectCurrentCart = '', releaseOldCart, oldCartStarted, denyOrders = false;
    let oldStarted = new Promise(resolve => { oldCartStarted = resolve; });
    const oldGate = new Promise(resolve => { releaseOldCart = resolve; });
    let completeOldRequest;
    const oldRequestFinished = new Promise(resolve => { completeOldRequest = resolve; });
    const headersSeen = [];
    await context.route('**/api/**', async route => {
      const req = route.request(), endpoint = new URL(req.url()).pathname.replace('/api', '');
      const auth = req.headers().authorization || '';
      const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status, data, msg: status === 401 ? '隔离测试：旧会话失效' : undefined }) });
      if (endpoint === '/admin/access') return send(require('./fixtures/admin-access.cjs')());
      if (endpoint === '/common/captcha/get') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ captchaKey: 'isolated', captchaImage: 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="110" height="34"><text x="12" y="25" font-size="23">Q7V2</text></svg>').toString('base64') }) });
      if (endpoint === '/common/auth/login' || endpoint === '/common/auth/adminLogin') {
        const data = req.postDataJSON();
        assert.equal(auth, '');
        const admin = endpoint.endsWith('adminLogin');
        return send({ token: `${admin ? 'admin' : 'user'}-${data.username}`, username: data.username, ...(admin ? { adminId: 1 } : { userId: data.username === 'buyer-A' ? 1 : 2 }) });
      }
      if (endpoint === '/fore/index/data') {
        headersSeen.push(auth);
        return send({ banners: [], hotProducts: [], newProducts: [], categories: [] });
      }
      if (endpoint === '/common/auth/logout') return send(null);
      if (endpoint === '/admin/user/list') return send(null, 403);
      if (endpoint === '/admin/order/list' && denyOrders) return send(null, 403);
      if (endpoint === '/fore/cart/list') {
        if (holdOldCart && auth === 'Bearer user-buyer-A') { oldCartStarted(); await oldGate; try { return await send(null, 401); } finally { completeOldRequest(); } }
        if (auth === rejectCurrentCart) return send(null, 401);
        return send([]);
      }
      if (endpoint === '/fore/user/profile') return send({ id: 2, username: auth.replace('Bearer user-', ''), email: '', phone: '' });
      if (endpoint === '/fore/product/collect' || endpoint === '/fore/user/address') return send([]);
      if (endpoint === '/fore/order/list' || endpoint === '/admin/product/list' || endpoint === '/admin/order/list') return send({ records: [], total: 0 });
      if (endpoint.endsWith('/workbench')) return send({});
      if (endpoint.endsWith('/revenue')) return send({ list: [] });
      return send([]);
    });
    async function newPage() { const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message)); return page; }
    const session = (page, domain) => page.evaluate(d => JSON.parse(sessionStorage.getItem(`qivo.session.v2.${d}`)), domain);
    async function login(page, name, admin = false) {
      if (admin) {
        await page.locator('#admin-account').fill(name);
        await page.locator('#admin-password').fill('isolated-password');
        await page.locator('.captcha img').waitFor();
        await page.locator('#admin-captcha').fill('Q7V2');
        await page.getByRole('button', { name: '登录管理台', exact: true }).click();
      } else {
        await page.locator('#customer-login-name').fill(name);
        await page.locator('#customer-login-password').fill('isolated-password');
        await page.getByRole('button', { name: '继续验证', exact: true }).click();
        await page.locator('.customer-auth__captcha img').waitFor();
        await page.locator('#customer-login-captcha').fill('Q7V2');
        await page.getByRole('button', { name: '登录', exact: true }).click();
      }
      await page.waitForURL(url => !url.pathname.endsWith('/login'));
    }
    const a = await newPage();
    await a.goto(base + '/login');
    await login(a, 'buyer-A');
    await a.reload();
    assert.equal((await session(a, 'USER')).username, 'buyer-A');
    const b = await newPage();
    await b.goto(base + '/user/profile');
    await b.waitForURL(url => url.pathname === '/login');
    assert.equal(await session(b, 'USER'), null);
    await b.screenshot({ path: path.join(out, '01-新标签需独立登录.png'), fullPage: true });
    await login(b, 'buyer-B');
    assert.equal((await session(a, 'USER')).username, 'buyer-A');
    assert.equal((await session(b, 'USER')).username, 'buyer-B');
    checks.push('同一浏览器两个标签独立登录不同消费者，刷新各自保留身份');

    const popupEvent = context.waitForEvent('page');
    await b.evaluate(url => { window.open(url, '_blank'); }, base + '/user/profile');
    const popup = await popupEvent;
    await popup.waitForURL(url => url.pathname === '/login');
    assert.equal(await session(popup, 'USER'), null);
    await popup.close();
    checks.push('window.open 创建的新窗口不继承 opener 的登录副本');

    holdOldCart = true;
    await a.goto(base + '/');
    await oldStarted;
    await a.locator('.account-menu-trigger').click();
    await a.getByRole('link', { name: '个人中心', exact: true }).click();
    await a.getByRole('button', { name: '退出登录', exact: true }).click();
    await a.waitForURL(url => url.pathname === '/login');
    await login(a, 'buyer-C');
    holdOldCart = false;
    releaseOldCart();
    await oldRequestFinished;
    await a.waitForTimeout(100);
    assert.equal((await session(a, 'USER')).username, 'buyer-C');
    assert.equal(new URL(a.url()).pathname, '/');
    assert.equal((await session(b, 'USER')).username, 'buyer-B');
    checks.push('旧账号延迟 401 不清除新账号，也不影响另一标签');

    rejectCurrentCart = 'Bearer user-buyer-C';
    await a.reload();
    await a.waitForFunction(() => sessionStorage.getItem('qivo.session.v2.USER') === null);
    assert.equal(new URL(a.url()).pathname, '/');
    rejectCurrentCart = '';
    assert(headersSeen.every(value => value === ''));
    checks.push('公开商品/首页请求不附带身份；过期会话清理后首页仍可匿名浏览');

    await b.goto(base + '/admin/product/list');
    await b.waitForURL(url => url.pathname === '/admin/login');
    assert.equal(await session(b, 'ADMIN'), null);
    await login(b, 'operator', true);
    await b.waitForURL(url => url.pathname === '/admin/product/list');
    assert.equal((await session(b, 'USER')).username, 'buyer-B');
    assert.equal(await b.getByText('查看商城', { exact: false }).count(), 0);
    assert.equal(await b.getByRole('link', { name: '栖物首页' }).count(), 0);
    await b.getByRole('link', { name: 'Qivo 管理工作台', exact: true }).click();
    await b.waitForURL(url => url.pathname === '/admin/dashboard');
    await b.screenshot({ path: path.join(out, '02-管理台独立导航.png'), fullPage: true });
    checks.push('消费者会话不能进入管理页；管理员 Logo 返回工作台，移除查看商城');
    await b.getByRole('link', { name: '用户管理', exact: true }).click();
    await b.getByRole('heading', { name: '没有访问权限', exact: true }).waitFor();
    assert.equal((await session(b, 'ADMIN')).username, 'operator');
    await b.screenshot({ path: path.join(out, '03-权限不足提示.png'), fullPage: true });
    checks.push('403 显示明确权限提示，不误判为网络故障、不清除有效管理员会话');
    denyOrders = true;
    await b.getByRole('link', { name: '工作台', exact: true }).click();
    await b.getByRole('heading', { name: '没有访问权限', exact: true }).waitFor();
    await b.getByText('今日订单金额', { exact: true }).waitFor();
    assert.equal(await b.getByRole('link', { name: '全部订单 →', exact: true }).count(), 0);
    await b.screenshot({ path: path.join(out, '04-工作台局部权限不足.png'), fullPage: true });
    checks.push('无订单权限时仅摘要受限，工作台指标继续显示，隐藏该摘要的订单入口');
    await b.getByRole('button', { name: '退出登录', exact: true }).click();
    await b.waitForURL(url => url.pathname === '/admin/login');
    assert.equal(await session(b, 'ADMIN'), null);
    assert.equal((await session(b, 'USER')).username, 'buyer-B');
    checks.push('同标签消费者/管理员会话分别存储，退出后台不退出消费者');
    await b.evaluate(() => localStorage.setItem('qivo.session.v2.USER', JSON.stringify({ token: 'legacy', username: 'legacy' })));
    await b.reload();
    assert.equal(await b.evaluate(() => localStorage.getItem('qivo.session.v2.USER')), null);
    assert.equal((await session(b, 'USER')).username, 'buyer-B');
    assert.deepEqual(errors, []);
    checks.push('旧 localStorage 会话清理但不迁移，当前标签 sessionStorage 保留；无运行时异常');
    await fs.writeFile(path.join(out, '专项回归记录.json'), JSON.stringify({ checks, errors }, null, 2));
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });

