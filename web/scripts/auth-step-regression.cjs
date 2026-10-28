// 独立构建 + 全量 API 拦截，仅使用内存测试数据，不访问业务服务。
const { chromium } = require(process.env.QIVO_PLAYWRIGHT_MODULE || 'playwright');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');

async function main() {
  const build = path.resolve(__dirname, '../..', process.env.QIVO_UI_BUILD || '.local/auth-step-build', 'index.html');
  const out = path.resolve(__dirname, '../..', process.env.QIVO_AUTH_OUT || 'docs/设计原型/登录注册实现-分步验证');
  await fs.mkdir(out, { recursive: true });
  const html = await fs.readFile(build);
  const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(html);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const checks = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    let captchaCount = 0, loginCount = 0, failCaptcha = false, failLogin = true, delayCaptcha = false;
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/**', async route => {
      const endpoint = new URL(route.request().url()).pathname;
      const send = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
      if (endpoint.endsWith('/captcha/get')) {
        const id = ++captchaCount;
        if (delayCaptcha) await new Promise(resolve => setTimeout(resolve, 450));
        if (failCaptcha) return send({ code: 500, msg: '隔离测试：验证码暂不可用' });
        const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="110" height="34"><rect width="110" height="34" fill="#edf1f8"/><text x="20" y="25" font-size="23" fill="#455872">Q7V2</text></svg>';
        return send({ captchaKey: `isolated-${id}`, captchaImage: 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64') });
      }
      if (endpoint.endsWith('/auth/login')) {
        loginCount++;
        const data = route.request().postDataJSON();
        assert.deepEqual(Object.keys(data).sort(), ['captchaCode', 'captchaKey', 'password', 'username']);
        assert.equal(data.username, 'isolated_buyer');
        assert.equal(data.password, 'test-password');
        assert.equal(data.captchaCode, 'Q7V2');
        if (failLogin) return send({ code: 400, msg: '隔离测试：验证码已失效' });
        return send({ code: 200, data: { token: 'isolated-token', userId: 98765, username: 'isolated_buyer' } });
      }
      if (endpoint.endsWith('/user/register')) return send({ code: 200, data: null });
      return send({ code: 200, data: [] });
    });
    const next = () => page.getByRole('button', { name: '继续验证', exact: true }).click();
    const captcha = page.locator('#customer-login-captcha');
    const ready = () => page.getByRole('button', { name: '登录', exact: true }).waitFor({ state: 'visible' });
    await page.goto(`http://127.0.0.1:${server.address().port}/login?redirect=/about`);
    await page.getByRole('heading', { name: '登录账号' }).waitFor();
    assert.equal(captchaCount, 0);
    await next();
    await page.getByText('请填写用户名和密码', { exact: true }).waitFor();
    assert.equal(loginCount, 0);
    await page.locator('#customer-login-name').fill('isolated_buyer');
    await page.locator('#customer-login-password').fill('test-password');
    await page.screenshot({ path: path.join(out, '01-账号密码.png'), fullPage: true });
    await page.locator('#customer-login-password').press('Enter');
    await ready();
    await page.getByRole('button', { name: '刷新验证码' }).waitFor();
    await page.locator('.customer-auth__captcha img').waitFor();
    assert.equal(loginCount, 0);
    assert.equal(await page.evaluate(() => sessionStorage.getItem('qivo.session.v2.USER')), null);
    assert(!page.url().includes('test-password'));
    await page.screenshot({ path: path.join(out, '02-图片验证.png'), fullPage: true });
    checks.push('第一步无登录/验证码请求；第二步不建立会话，保留登录后返回地址');
    await page.getByRole('button', { name: '返回账号填写' }).click();
    assert.equal(await page.locator('#customer-login-password').inputValue(), 'test-password');
    failCaptcha = true;
    await next();
    await page.getByText('验证码未加载成功，点击右侧重试').waitFor();
    assert(await page.getByRole('button', { name: '登录' }).isDisabled());
    await page.screenshot({ path: path.join(out, '03-验证码加载失败.png'), fullPage: true });
    failCaptcha = false;
    await page.getByRole('button', { name: '重新获取验证码', exact: true }).click();
    await page.locator('.customer-auth__captcha img').waitFor();
    await captcha.fill('Q7V2');
    await captcha.press('Enter');
    await page.getByText('隔离测试：验证码已失效').waitFor();
    await page.locator('.customer-auth__captcha img').waitFor();
    assert.equal(await captcha.inputValue(), '');
    assert.equal(loginCount, 1);
    checks.push('返回保留账号密码；验证码失败重试；登录失败后刷新验证码且不建立会话');
    delayCaptcha = true;
    await page.getByRole('button', { name: '刷新验证码' }).click();
    await page.getByRole('button', { name: '返回账号填写' }).click();
    await page.waitForTimeout(550);
    assert.equal(await captcha.count(), 0);
    delayCaptcha = false;
    await next();
    await page.locator('.customer-auth__captcha img').waitFor();
    await captcha.fill('Q7V2');
    failLogin = false;
    await captcha.press('Enter');
    await page.waitForURL(url => url.pathname === '/about');
    assert.equal(loginCount, 2);
    const session = await page.evaluate(() => JSON.parse(sessionStorage.getItem('qivo.session.v2.USER')));
    assert.equal(session.token, 'isolated-token');
    assert(!('password' in session));
    checks.push('返回时忽略过期验证码请求；最终提交原四字段，成功后才保存会话并跳转');
    await page.evaluate(() => sessionStorage.clear());
    await page.goto(`http://127.0.0.1:${server.address().port}/register`);
    await page.locator('#customer-register-name').fill('isolated_buyer');
    await page.locator('#customer-register-password').fill('test-password');
    await page.locator('#customer-register-confirm').fill('test-password');
    await page.waitForTimeout(750);
    await page.screenshot({ path: path.join(out, '04-注册.png'), fullPage: true });
    await page.getByRole('button', { name: '创建账号', exact: true }).click();
    await page.getByRole('heading', { name: '登录账号' }).waitFor();
    assert.equal(await page.locator('#customer-login-name').inputValue(), 'isolated_buyer');
    checks.push('注册保留原接口，成功后回到账号密码步骤并预填用户名');
    for (const [width, height] of [[1280,720],[1440,900],[1920,1080],[1280,600],[390,844]]) {
      await page.setViewportSize({ width, height });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      assert.equal(await page.locator('.customer-auth__header img, .customer-auth__footer').count(), 0);
      await page.screenshot({ path: path.join(out, `布局-${width}x${height}.png`), fullPage: true });
    }
    assert.deepEqual(errors, []);
    checks.push('1280/1440/1920、矮窗口和窄屏无横向溢出；无左上品牌/左下标签，无运行时异常');
    await fs.writeFile(path.join(out, '验证记录.json'), JSON.stringify({ checks, captchaRequests: captchaCount, loginRequests: loginCount, errors }, null, 2));
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
