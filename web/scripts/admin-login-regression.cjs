// 独立构建与模拟接口；不连接日常业务服务。
const { chromium } = require(process.env.QIVO_PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');

async function main() {
  const root = path.resolve(__dirname, '../..');
  const out = path.join(root, process.env.QIVO_ADMIN_AUTH_OUT || 'docs/设计原型/管理员登录实现-清晰工作台版');
  await fs.mkdir(out, { recursive: true });
  const html = await fs.readFile(path.join(root, process.env.QIVO_UI_BUILD || 'web/dist', 'index.html'));
  const server = http.createServer((req, res) => { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const checks = [], errors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const base = `http://127.0.0.1:${server.address().port}`;
    let failCaptcha = false, failLogin = true, captchaCount = 0, loginCount = 0;
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/**', async route => {
      const endpoint = new URL(route.request().url()).pathname;
      const send = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
      if (endpoint === '/api/admin/access') return send({code:200,data:require('./fixtures/admin-access.cjs')(98765)});
      if (endpoint.endsWith('/captcha/get')) {
        const id = ++captchaCount;
        await new Promise(resolve => setTimeout(resolve, 150));
        if (failCaptcha) return send({ code: 500, msg: '隔离测试：验证码不可用' });
        const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="110" height="34"><rect width="110" height="34" fill="#edf1e8"/><text x="14" y="25" fill="#566651" font-size="23">Q7v2</text></svg>';
        return send({ captchaKey: `isolated-${id}`, captchaImage: 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64') });
      }
      if (endpoint.endsWith('/auth/adminLogin')) {
        loginCount++;
        assert.deepEqual(route.request().postDataJSON(), { username: 'isolated-admin', password: 'test-password', captchaKey: `isolated-${captchaCount}`, captchaCode: 'Q7v2' });
        await new Promise(resolve => setTimeout(resolve, 200));
        if (failLogin) return send({ code: 400, msg: '隔离测试：登录失败' });
        return send({ code: 200, data: { token: 'isolated-admin-token', adminId: 98765, username: 'isolated-admin' } });
      }
      if (endpoint.endsWith('/auth/login')) throw new Error('不应调用消费者登录接口');
      return send({ code: 200, data: [] });
    });
    await page.goto(base + '/admin/login?redirect=/about');
    const submit = page.getByRole('button', { name: '登录管理台', exact: true });
    const captchaImage = page.locator('.captcha img');
    await captchaImage.waitFor();
    assert.equal(await page.getByRole('link').count(), 0);
    await page.screenshot({ path: path.join(out, '01-管理员登录.png'), fullPage: true });
    await submit.click();
    await page.getByText('请填写管理员账号', { exact: true }).waitFor();
    assert.equal(await page.locator('#admin-account').getAttribute('aria-invalid'), 'true');
    assert(await page.locator('#admin-account').evaluate(e => e === document.activeElement));
    assert.equal(loginCount, 0);
    await page.locator('#admin-account').fill('isolated-admin');
    await page.locator('#admin-password').fill('test-password');
    await page.getByRole('button', { name: '显示密码', exact: true }).click();
    assert.equal(await page.locator('#admin-password').getAttribute('type'), 'text');
    await page.getByRole('button', { name: '隐藏密码', exact: true }).click();
    failCaptcha = true;
    await page.getByRole('button', { name: '刷新验证码' }).click();
    await page.getByText('验证码加载失败，点击右侧重试', { exact: true }).waitFor();
    assert(await submit.isDisabled());
    await page.screenshot({ path: path.join(out, '02-验证码失败重试.png'), fullPage: true });
    failCaptcha = false;
    await page.getByRole('button', { name: '重新获取验证码', exact: true }).click();
    await captchaImage.waitFor();
    await submit.click();
    await page.getByText('请输入图片中的验证码', { exact: true }).waitFor();
    checks.push('无商城跳转入口；必填校验、密码显隐及验证码加载失败重试');
    await page.locator('#admin-captcha').fill('Q7v2');
    await page.locator('#admin-captcha').press('Enter');
    await page.getByRole('button', { name: '登录中…' }).waitFor();
    assert(await page.getByRole('button', { name: '登录中…' }).isDisabled());
    await page.getByText('隔离测试：登录失败').waitFor();
    await captchaImage.waitFor();
    assert.equal(await page.locator('#admin-password').inputValue(), 'test-password');
    assert.equal(await page.locator('#admin-captcha').inputValue(), '');
    assert.equal(await page.evaluate(() => sessionStorage.getItem('qivo.session.v2.ADMIN')), null);
    checks.push('Enter 提交原四字段；提交时锁定表单；失败保留账号密码并刷新验证码');
    await page.evaluate(() => sessionStorage.setItem('qivo.session.v2.USER', JSON.stringify({ token: 'isolated-user-token', username: 'isolated-buyer', userId: 123 })));
    await page.locator('#admin-captcha').fill('Q7v2');
    failLogin = false;
    await submit.click();
    await page.waitForURL(url => url.pathname === '/admin/dashboard');
    const sessions = await page.evaluate(() => ({ admin: JSON.parse(sessionStorage.getItem('qivo.session.v2.ADMIN')), user: JSON.parse(sessionStorage.getItem('qivo.session.v2.USER')) }));
    assert.equal(sessions.admin.token, 'isolated-admin-token');
    assert.equal(sessions.user.token, 'isolated-user-token');
    assert.equal(loginCount, 2);
    checks.push('成功保存管理员会话且不覆盖消费者会话；跨身份返回地址回退至管理台');
    await page.goto(base + '/admin/login');
    await captchaImage.waitFor();
    for (const [width, height] of [[1280,720],[1440,900],[1920,1080],[1280,600],[390,844]]) {
      await page.setViewportSize({ width, height });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: path.join(out, `布局-${width}x${height}.png`), fullPage: true });
    }
    assert.deepEqual(errors, []);
    checks.push('三种桌面宽度、矮窗口及窄屏无横向溢出，无浏览器运行时异常');
    await fs.writeFile(path.join(out, '验证记录.json'), JSON.stringify({ checks, errors, loginRequests: loginCount }, null, 2));
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
