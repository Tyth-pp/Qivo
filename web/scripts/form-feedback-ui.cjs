const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),http=require('node:http'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
const root=path.resolve(__dirname,'../..'),out=path.join(root,process.env.QIVO_FEEDBACK_OUT||'docs/设计原型/表单反馈优化验收');await fs.mkdir(out,{recursive:true});
const server=http.createServer(async(req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(await fs.readFile(path.join(root,'web/dist/index.html')));});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const base='http://127.0.0.1:'+server.address().port,browser=await chromium.launch({channel:'msedge',headless:true});
let posts=0,mode='duplicate';
try{
const page=await browser.newPage({viewport:{width:1440,height:960},deviceScaleFactor:1}),errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.route('**/api/**',async route=>{
const p=new URL(route.request().url()).pathname;
const send=(data,code=200,msg='')=>route.fulfill({status:code===200?200:400,contentType:'application/json',body:JSON.stringify({code,data,msg})});
if(p.endsWith('/captcha/get'))return route.fulfill({contentType:'application/json',body:JSON.stringify({captchaKey:'test',captchaImage:'data:image/svg+xml;base64,'+Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="110" height="36"><rect width="110" height="36" fill="#f1f3f8"/><text x="14" y="25">A7X2</text></svg>').toString('base64')})});
if(p.endsWith('/user/register')){posts++;if(mode==='network')return route.abort('failed');if(mode==='legacy')return send(null,400,'username: 需要匹配正则表达式 [a-zA-Z0-9_]{3,50}');if(mode==='duplicate')return send(null,1004,'用户名已存在');await new Promise(r=>setTimeout(r,300));return send(null);}
if(p.endsWith('/auth/login'))return send(null,400,'验证码错误');
if(p.endsWith('/auth/adminLogin'))return send(null,1003,'账号已被禁用');
return send({categories:[],records:[],total:0});
});
await page.goto(base+'/register');await page.locator('#customer-register-name').waitFor();
await page.locator('#customer-register-name').fill('中文用户');await page.locator('#customer-register-password').fill('isolatedPassword9');await page.locator('#customer-register-confirm').fill('isolatedPassword9');
await page.getByRole('button',{name:'创建账号',exact:true}).click();assert.equal(posts,0);assert(await page.locator('#customer-register-name').evaluate(el=>el===document.activeElement));
assert((await page.locator('#register-username-feedback').innerText()).includes('暂不支持中文'));
assert.equal(await page.locator('#customer-register-password').inputValue(),'isolatedPassword9');
for(const width of [1280,1440,1920]){await page.setViewportSize({width,height:960});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 const card=await page.locator('.customer-auth__card').boundingBox(),button=await page.getByRole('button',{name:'创建账号',exact:true}).boundingBox();assert(button.y+button.height<=card.y+card.height);
 await page.screenshot({path:path.join(out,'用户名格式提示-'+width+'.png'),fullPage:true});}
await page.locator('#customer-register-name').fill('valid_user9');await page.getByRole('button',{name:'创建账号',exact:true}).click();await page.getByText('这个用户名已被使用，请换一个试试',{exact:true}).waitFor();assert.equal(await page.locator('.el-message').count(),0);
await page.screenshot({path:path.join(out,'用户名已使用.png'),fullPage:true});
mode='legacy';await page.getByRole('button',{name:'创建账号',exact:true}).click();await page.getByText('用户名需为3–50位字母、数字或下划线，暂不支持中文',{exact:true}).waitFor();assert(!/正则表达式|username:/.test(await page.locator('.customer-auth__panel--register').innerText()));
mode='network';await page.getByRole('button',{name:'创建账号',exact:true}).click();await page.getByText(/网络连接不稳定/).waitFor();assert.equal(await page.locator('#customer-register-name').inputValue(),'valid_user9');assert.equal(await page.locator('.el-message').count(),0);
await page.screenshot({path:path.join(out,'网络失败保留填写内容.png'),fullPage:true});
await page.locator('#customer-register-confirm').fill('differentPassword9');const count=posts;await page.getByRole('button',{name:'创建账号',exact:true}).click();assert.equal(posts,count);await page.getByText('两次输入的密码不一致，请重新确认',{exact:true}).waitFor();
await page.locator('#customer-register-confirm').fill('isolatedPassword9');mode='success';await page.getByRole('button',{name:'创建账号',exact:true}).click();await page.waitForURL('**/login');assert.equal(await page.locator('#customer-login-name').inputValue(),'valid_user9');
await page.locator('#customer-login-password').fill('isolatedPassword9');await page.getByRole('button',{name:'继续验证',exact:true}).click();await page.locator('#customer-login-captcha').fill('wrong');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByText('验证码错误',{exact:true}).waitFor();
await page.getByRole('button',{name:'返回账号填写',exact:true}).click();assert.equal(await page.locator('#customer-login-name').inputValue(),'valid_user9');assert.equal(await page.locator('#customer-login-password').inputValue(),'isolatedPassword9');
await page.goto(base+'/admin/login');await page.getByRole('textbox',{name:'管理员账号',exact:true}).fill('test_admin');
await page.locator('input[autocomplete=current-password]').fill('isolatedPassword9');await page.locator('input[name=captchaCode]').fill('A7X2');await page.getByRole('button',{name:'登录管理台',exact:true}).click();await page.getByText('当前账号暂时无法使用，请联系管理员',{exact:true}).waitFor();assert.equal(await page.locator('.el-message').count(),0);
assert.deepEqual(errors,[]);
await fs.writeFile(path.join(out,'检查记录.json'),JSON.stringify({widths:[1280,1440,1920],pageErrors:errors,checks:['中文用户名前置拦截且不发请求','错误字段自动聚焦','三种桌面宽度按钮不被裁切','用户名重复的可执行提示','兼容旧后端正则报错','网络失败保留填写且无重复toast','确认密码不一致前置拦截','注册成功保留登录名','验证码失败及返回保留账号密码','管理员账号停用提示']},null,2));console.log('Friendly form feedback checks passed');
}finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
