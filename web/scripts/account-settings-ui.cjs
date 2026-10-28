// Actual build, isolated API fixtures. No business-service traffic or real account writes.
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
const http=require('node:http'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/双端账号设置验收');await fs.mkdir(out,{recursive:true});
const pixel=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=','base64');
const avatar='/upload/avatars/2026/10/27/12345678-1234-1234-1234-123456789abc.png';
const server=http.createServer(async(req,res)=>{if(req.url===avatar){res.setHeader('Content-Type','image/png');res.end(pixel);}else{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(await fs.readFile(path.join(root,'web/dist/index.html')));}});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const base='http://127.0.0.1:'+server.address().port,browser=await chromium.launch({channel:'msedge',headless:true});
let buyer={id:1,username:'buyer',email:'buyer@example.test',phone:'13800000123',avatar:''},admin={id:1,username:'admin',realName:'管理员',avatar:''};
let failure='',events=[],unexpected=[];
try{
const context=await browser.newContext({viewport:{width:1440,height:960},deviceScaleFactor:1});
await context.addInitScript(()=>{
sessionStorage.setItem('qivo.session.v2.USER',JSON.stringify({token:'isolated-user',userId:1,username:'buyer'}));
sessionStorage.setItem('qivo.session.v2.ADMIN',JSON.stringify({token:'isolated-admin',adminId:1,username:'admin'}));
});
await context.route('**/api/**',async route=>{
const req=route.request(),p=new URL(req.url()).pathname.replace(/^\/api/,''),method=req.method();events.push({p,method,auth:req.headers().authorization});
const send=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status,data,msg:status===200?'':'隔离请求失败'})});
if(p==='/fore/index/data')return send({categories:[],hotProducts:[],newProducts:[]});
if(p==='/fore/product/collect'||p==='/fore/cart/list')return send([]);
if(p==='/fore/user/profile'){if(method==='PUT')Object.assign(buyer,req.postDataJSON());return send(buyer);}
if(p==='/admin/profile'){if(method==='PUT')Object.assign(admin,req.postDataJSON());return send(admin);}
if(p==='/admin/access')return send(require('./fixtures/admin-access.cjs')());
if(p==='/admin/statistics/workbench')return send({todayRevenue:0,totalOrders:0,paidOrders:0,unpaidOrders:0,onShelf:0,offShelf:0});
if(p==='/admin/order/list')return send({records:[],total:0});
if(p==='/admin/statistics/revenue')return send({list:[]});
if(p==='/admin/statistics/productTop10')return send({list:[]});
if(p==='/common/upload/avatar'){if(failure==='upload')return send(null,500);return send(avatar);}
if(p==='/fore/user/password'||p==='/admin/password'){if(failure==='password')return route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:1002,msg:'密码错误'})});return send(null);}
if(p==='/common/captcha/get')return route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,captchaKey:'isolated',captchaImage:'data:image/png;base64,'+pixel.toString('base64')})});
unexpected.push(p);return send(null,404);
});
const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(base+'/user/profile?tab=info');await page.getByRole('button',{name:'更换头像',exact:true}).waitFor();
for(const width of [1280,1440,1920]){await page.setViewportSize({width,height:960});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'用户账号资料-'+width+'.png'),fullPage:true});}
const upload=()=>page.locator('input[type=file]').setInputFiles({name:'avatar.png',mimeType:'image/png',buffer:pixel});
await upload();await page.waitForFunction(()=>document.querySelector('.avatar-editor img')!==null);
assert.equal(buyer.avatar,''); // draft until Save
await page.getByRole('button',{name:'保存资料',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.profile-side img')!==null);assert.equal(buyer.avatar,avatar);
failure='upload';await upload();await page.waitForTimeout(180);assert.equal(await page.locator('.avatar-editor img').getAttribute('src'),avatar);failure='';
await page.setViewportSize({width:1440,height:960});await page.getByRole('button',{name:'修改密码',exact:true}).click();await page.getByRole('dialog',{name:'修改密码',exact:true}).waitFor();await page.waitForTimeout(350);await page.waitForFunction(()=>document.querySelectorAll('.el-message').length===0);await page.screenshot({path:path.join(out,'用户修改密码.png'),fullPage:true});
async function fillPassword(old,next,confirm){await page.getByRole('textbox',{name:'当前密码',exact:true}).fill(old);await page.getByRole('textbox',{name:'新密码',exact:true}).fill(next);await page.getByRole('textbox',{name:'确认新密码',exact:true}).fill(confirm);}
await fillPassword('beforePass9','afterPass9','different9');const before=events.filter(e=>e.p.endsWith('/password')).length;await page.getByRole('button',{name:'保存并重新登录',exact:true}).click();assert.equal(events.filter(e=>e.p.endsWith('/password')).length,before);
await fillPassword('beforePass9','afterPass9','afterPass9');failure='password';await page.getByRole('button',{name:'保存并重新登录',exact:true}).click();await page.waitForTimeout(160);assert.equal(await page.getByRole('textbox',{name:'当前密码',exact:true}).inputValue(),'beforePass9');assert(await page.evaluate(()=>sessionStorage.getItem('qivo.session.v2.USER')));await page.getByText('当前密码不正确，请重新输入',{exact:true}).waitFor();failure='';
await page.getByRole('button',{name:'保存并重新登录',exact:true}).click();await page.waitForURL('**/login');
assert.equal(await page.evaluate(()=>sessionStorage.getItem('qivo.session.v2.USER')),null);assert(await page.evaluate(()=>sessionStorage.getItem('qivo.session.v2.ADMIN')));
await page.setViewportSize({width:1440,height:960});await page.goto(base+'/admin/dashboard');await page.getByRole('button',{name:'账号设置',exact:true}).waitFor();await page.getByRole('button',{name:'账号设置',exact:true}).click();await page.getByRole('textbox',{name:'显示名称',exact:true}).waitFor();
await page.waitForTimeout(350);
for(const width of [1280,1440,1920]){await page.setViewportSize({width,height:960});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'管理员账号设置-'+width+'.png'),fullPage:true});}
await page.setViewportSize({width:1440,height:960});await page.screenshot({path:path.join(out,'管理员账号设置.png'),fullPage:true});
await page.getByRole('textbox',{name:'显示名称',exact:true}).fill('新的名称');await upload();await page.waitForFunction(()=>document.querySelector('.avatar-editor img')!==null);await page.getByRole('button',{name:'保存资料',exact:true}).click();await page.waitForTimeout(180);assert.equal(admin.realName,'新的名称');assert.equal(admin.avatar,avatar);
await page.getByRole('textbox',{name:'显示名称',exact:true}).fill('未保存名称');await page.keyboard.press('Escape');await page.getByRole('dialog',{name:'放弃修改',exact:true}).getByRole('button',{name:'取消',exact:true}).click();assert.equal(await page.getByRole('textbox',{name:'显示名称',exact:true}).inputValue(),'未保存名称');
await page.getByRole('textbox',{name:'显示名称',exact:true}).fill('新的名称');await page.getByRole('tab',{name:'修改密码',exact:true}).click();await page.getByRole('textbox',{name:'当前密码',exact:true}).waitFor();await page.waitForTimeout(350);await page.screenshot({path:path.join(out,'管理员修改密码.png'),fullPage:true});
await fillPassword('beforePass9','afterPass9','afterPass9');await page.getByRole('button',{name:'保存并重新登录',exact:true}).click();await page.waitForURL('**/admin/login');assert.equal(await page.evaluate(()=>sessionStorage.getItem('qivo.session.v2.ADMIN')),null);
assert(events.some(e=>e.p==='/common/upload/avatar'&&e.auth==='Bearer isolated-user'));assert(events.some(e=>e.p==='/common/upload/avatar'&&e.auth==='Bearer isolated-admin'));
assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
await fs.writeFile(path.join(out,'检查记录.json'),JSON.stringify({widths:[1280,1440,1920],pageErrors:errors,unexpected,checks:['头像上传与保存分离','上传失败保留原图','资料保存刷新侧栏','密码前端校验','密码请求失败保留输入','改密后仅退出当前身份','双端上传分别带对应身份','管理员资料保存','抽屉丢弃修改确认与取消保留','双端入口和样式截图'],requests:events.map(({p,method})=>({p,method}))},null,2));
console.log('Account settings UI checks passed');
}finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
