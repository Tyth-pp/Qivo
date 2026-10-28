// 权限元数据、菜单、路由、操作和身份切换；全部 API 使用隔离内存数据。
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const all=require('./fixtures/admin-access.cjs')().capabilities;
async function main(){
  const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/权限一致性验收');
  await fs.mkdir(out,{recursive:true});
  const html=await fs.readFile(path.join(root,process.env.QIVO_UI_BUILD||'.local/admin-access-build','index.html'));
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html)});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const browser=await chromium.launch({channel:'msedge',headless:true}),checks=[],errors=[];
  const profiles={super:{...all},reader:{'product:list':true},editor:{'product:list':true,'category:list':true,'product:add':true,'product:update':true},orders:{'order:list':true},shipper:{'order:list':true,'order:ship':true},users:{'user:list':true},roles:{'role:list':true},empty:{},broken:{'product:list':true},mismatch:{...all}};
  const events=[];let broken=true,holdEditor=false,releaseOld,oldStarted;
  let oldStartedPromise=new Promise(r=>oldStarted=r),oldGate=new Promise(r=>releaseOld=r);
  const product={id:101,name:'隔离测试商品',price:100,stock:20,totalStock:20,sales:1,status:1,categoryId:1,specMode:'SINGLE',defaultSkuId:201,mainImage:''};
  const orders=[{id:301,orderNo:'ISOLATED-301',payStatus:1,shipStatus:0,totalAmount:100,createTime:new Date().toLocaleString('sv-SE')},{id:302,orderNo:'ISOLATED-302',payStatus:0,shipStatus:0,totalAmount:100,createTime:new Date().toLocaleString('sv-SE')}];
  try{
    const context=await browser.newContext({viewport:{width:1440,height:900}});context.setDefaultTimeout(10000);
    const base=`http://127.0.0.1:${server.address().port}`;
    await context.route('**/api/**',async route=>{
      const req=route.request(),url=new URL(req.url()),p=url.pathname.replace('/api',''),actor=(req.headers().authorization||'').replace('Bearer isolated-','');
      events.push({p,actor,method:req.method()});
      const send=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status,data,msg:status===200?undefined:'隔离测试请求失败'})});
      if(p==='/common/captcha/get')return route.fulfill({contentType:'application/json',body:JSON.stringify({captchaKey:'isolated',captchaImage:'data:image/svg+xml;base64,'+Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="110" height="34"><text x="12" y="25" font-size="23">Q7V2</text></svg>').toString('base64')})});
      if(p==='/common/auth/adminLogin'){const body=req.postDataJSON();return send({token:'isolated-'+body.username,adminId:1,username:body.username})}
      if(p==='/common/auth/logout')return send(null);
      if(p==='/admin/access'){
        const snapshot={...(profiles[actor]||{})};
        if(actor==='broken'&&broken)return send(null,503);
        if(actor==='editor'&&holdEditor){oldStarted();await oldGate}
        return send({adminId:actor==='mismatch'?999:1,username:actor,permissions:[],capabilities:snapshot});
      }
      if(p==='/admin/product/list')return send({records:[product],total:1});
      if(p==='/admin/product/category/list')return send([{id:1,parentId:0,name:'测试分类',status:1}]);
      if(p==='/admin/product/detail/101')return send({...product,description:'测试商品',specDefinitions:[],skus:[{id:201,price:100,stock:20,status:'ENABLED',specValues:'[]',image:''}]});
      if(p.endsWith('/stock-movements'))return send({records:[],total:0});
      if(p==='/admin/order/list')return send({records:orders,total:2});
      if(p.startsWith('/admin/order/detail/'))return send(orders.find(x=>x.id===Number(p.split('/').pop())));
      if(p==='/admin/user/list')return send({records:[{id:501,username:'isolated-buyer',status:1,score:10}],total:1});
      if(p==='/admin/role/list')return send([{id:2,name:'自定义只读角色',code:'custom-reader'}]);
      if(p==='/admin/permission/tree')return send([{id:1,name:'商品查看',code:'product:list',parentId:0}]);
      if(p.endsWith('/permissions'))return send([]);
      if(p==='/admin/statistics/workbench')return send({todayRevenue:100,totalOrders:1,paidOrders:1,unpaidOrders:0,onShelf:1,offShelf:0});
      if(p==='/admin/statistics/revenue')return send({list:[]});
      return send([]);
    });
    async function login(name,expected,redirect='/admin/dashboard'){
      const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
      await page.goto(base+'/admin/login?redirect='+encodeURIComponent(redirect));
      await fillLogin(page,name,expected);return page;
    }
    async function fillLogin(page,name,expected){
      await page.locator('#admin-account').fill(name);await page.locator('#admin-password').fill('isolated-password');
      await page.locator('.captcha img').waitFor();await page.locator('#admin-captcha').fill('Q7V2');
      await page.getByRole('button',{name:'登录管理台',exact:true}).click();await page.waitForURL(u=>u.pathname===expected);
      await page.locator('.admin-shell').waitFor();
    }
    const nav=p=>p.locator('.admin-nav');
    const reader=await login('reader','/admin/product/list','/admin/user/list');
    await reader.getByText('隔离测试商品',{exact:true}).waitFor();
    assert.equal(await nav(reader).getByRole('link').count(),1);
    assert.equal(await reader.getByRole('button',{name:'＋ 添加商品',exact:true}).count(),0);
    assert.equal(await reader.getByRole('button',{name:'编辑',exact:true}).count(),0);
    assert.equal(await reader.getByRole('button',{name:'下架',exact:true}).count(),0);
    assert(!events.some(e=>e.actor==='reader'&&e.p==='/admin/product/category/list'));
    await reader.screenshot({path:path.join(out,'01-商品只读账号.png'),fullPage:true});
    await reader.goto(base+'/admin/user/list');await reader.waitForURL(u=>u.pathname==='/admin/access');
    await reader.getByRole('heading',{name:'当前页面不可访问'}).waitFor();
    assert(!events.some(e=>e.actor==='reader'&&e.p==='/admin/user/list'));
    await reader.screenshot({path:path.join(out,'02-手动访问越权页面.png'),fullPage:true});
    checks.push('商品只读账号只有商品菜单，无写按钮；不请求无权分类；越权地址在页面加载前被拦截，登录落点回退到已授权页面');
    const editor=await login('editor','/admin/product/list');
    await editor.getByRole('button',{name:'编辑',exact:true}).waitFor();
    assert.equal(await nav(editor).getByRole('link').count(),2);
    await editor.getByRole('button',{name:'编辑',exact:true}).click();
    profiles.editor={'product:list':true,'category:list':true};
    await editor.evaluate(()=>window.dispatchEvent(new Event('focus')));
    await editor.waitForFunction(()=>document.querySelector('.admin-page')?.textContent.includes('保存商品信息')&&!Array.from(document.querySelectorAll('.el-table button')).some(b=>b.textContent.trim()==='编辑'));
    assert(await editor.getByRole('button',{name:'保存商品信息',exact:true}).isDisabled());
    await editor.getByRole('button',{name:'关闭',exact:true}).click();
    checks.push('编辑权限撤销后刷新当前权限，旧编辑抽屉保存按钮失效，列表编辑按钮消失');
    profiles.editor={};await editor.evaluate(()=>window.dispatchEvent(new Event('focus')));
    await editor.getByRole('heading',{name:'没有访问权限',exact:true}).waitFor();
    assert.equal(await nav(editor).getByRole('link').count(),0);
    await editor.screenshot({path:path.join(out,'03-权限撤销.png'),fullPage:true});
    checks.push('页面查看权限撤销后卸载原业务内容并隐藏菜单，不继续展示旧授权页面');
    const orderReader=await login('orders','/admin/order/list');
    await orderReader.getByRole('button',{name:'详情',exact:true}).first().waitFor();
    assert.equal(await orderReader.getByRole('button',{name:'发货',exact:true}).count(),0);
    assert.equal(await orderReader.getByRole('button',{name:'改价',exact:true}).count(),0);
    const shipper=await login('shipper','/admin/order/list');await shipper.getByRole('button',{name:'发货',exact:true}).waitFor();
    assert.equal(await shipper.getByRole('button',{name:'改价',exact:true}).count(),0);
    checks.push('订单只读不能发货/改价；发货账号可发货，不能获得超级管理员专属改价权限');
    const users=await login('users','/admin/user/list');await users.getByText('isolated-buyer',{exact:true}).waitFor();
    assert.equal(await users.getByRole('button',{name:'改积分',exact:true}).count(),0);assert.equal(await users.getByRole('button',{name:'禁用',exact:true}).count(),0);
    const roles=await login('roles','/admin/role/list');await roles.getByText('当前为只读权限',{exact:true}).waitFor();
    assert.equal(await roles.getByRole('button',{name:'＋ 添加角色',exact:true}).count(),0);assert.equal(await roles.getByRole('button',{name:'保存权限',exact:true}).count(),0);
    await roles.screenshot({path:path.join(out,'04-角色只读.png'),fullPage:true});
    checks.push('用户只读不显示禁用/积分修改；角色只读禁用权限树且没有角色新增、编辑、删除、授权操作');
    const empty=await login('empty','/admin/access');await empty.getByText('当前账号还没有可访问的管理功能，请联系管理员分配权限。',{exact:true}).waitFor();
    assert.equal(await nav(empty).getByRole('link').count(),0);
    const unavailable=await login('broken','/admin/access');await unavailable.getByRole('heading',{name:'权限信息暂不可用',exact:true}).waitFor();
    broken=false;await unavailable.getByRole('button',{name:'重新获取权限',exact:true}).click();await unavailable.waitForURL(u=>u.pathname==='/admin/product/list');
    const mismatch=await login('mismatch','/admin/access');await mismatch.getByRole('heading',{name:'权限信息暂不可用',exact:true}).waitFor();
    checks.push('无页面权限、权限接口失败、响应账号不匹配均不放行；主动重试后可恢复');
    profiles.editor={'product:list':true,'category:list':true,'product:update':true};
    const switching=await login('editor','/admin/product/list');
    holdEditor=true;await switching.evaluate(()=>window.dispatchEvent(new Event('focus')));await oldStartedPromise;
    await switching.getByRole('button',{name:'退出登录',exact:true}).click();await switching.waitForURL(u=>u.pathname==='/admin/login');
    await fillLogin(switching,'reader','/admin/product/list');holdEditor=false;releaseOld();await switching.waitForTimeout(150);
    assert.equal(await switching.getByRole('button',{name:'编辑',exact:true}).count(),0);assert.equal(await nav(switching).getByRole('link').count(),1);
    checks.push('切换账号时旧账号延迟权限响应不能恢复新账号的菜单或写权限');
    const superAdmin=await login('super','/admin/dashboard');assert.equal(await nav(superAdmin).getByRole('link').count(),9);
    await superAdmin.screenshot({path:path.join(out,'05-超级管理员.png'),fullPage:true});
    assert.deepEqual(errors,[]);assert(!events.some(e=>['reader','orders','users','roles','empty'].includes(e.actor)&&e.method!=='GET'&&!e.p.startsWith('/common/auth/')));
    checks.push('超级管理员保留全部菜单；只读账号未发出业务写请求，无浏览器运行异常');
    await fs.writeFile(path.join(out,'专项回归记录.json'),JSON.stringify({checks,errors,requests:events},null,2));
    console.log(JSON.stringify({passed:checks.length,checks},null,2));
  }finally{releaseOld?.();await browser.close();await new Promise(r=>server.close(r))}
}
main().catch(e=>{console.error(e);process.exitCode=1});
