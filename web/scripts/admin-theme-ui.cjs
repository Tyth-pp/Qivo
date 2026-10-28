// 真实构建产物 + 全 API 拦截；检查 A 方案新增行为，不连接日常业务。
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),http=require('node:http'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/管理端A方案正式验收');await fs.mkdir(out,{recursive:true});
 const server=http.createServer(async(req,res)=>{res.setHeader('Content-Type','text/html;charset=utf-8');res.end(await fs.readFile(path.join(root,'web/dist/index.html')));});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const b=await chromium.launch({channel:'msedge',headless:true}),p=await b.newPage({viewport:{width:1440,height:960}});const errors=[],writes=[];let limited=false;
 p.on('pageerror',e=>errors.push(e.message));
 await p.addInitScript(()=>{if(!sessionStorage.getItem('qivo.session.v2.ADMIN'))sessionStorage.setItem('qivo.session.v2.ADMIN',JSON.stringify({token:'theme-isolated',adminId:1,username:'isolated-admin'}));});
 await p.route('**/api/**',async route=>{const req=route.request(),url=new URL(req.url()),u=url.pathname;if(req.method()!=='GET')writes.push(u);let data=[];
 if(u.endsWith('/admin/access')){data=require('./fixtures/admin-access.cjs')();if(limited)data.capabilities={dashboard:true};}
 else if(u.endsWith('/admin/profile'))data={username:'isolated-admin',realName:'管理者',avatar:''};
 else if(u.endsWith('/orderStats'))data={totalOrders:36,validOrders:28};
 else if(u.endsWith('/workbench'))data={todayRevenue:1268,totalOrders:8,paidOrders:6,unpaidOrders:2,onShelf:5,offShelf:1};
 else if(u.endsWith('/revenue'))data={list:url.searchParams.get('type')==='month'?[{month:1,amount:0},{month:2,amount:120}]:[{date:'2026-10-25',amount:0,count:0},{date:'2026-10-26',amount:null,count:null},{date:'2026-10-27',amount:160,count:2}]};
 else if(u.endsWith('/productTop10'))data={list:[]};
 else if(u.endsWith('/order/list'))data={records:[],total:0};
 else if(u.endsWith('/index/data'))data={categories:[],hotProducts:[],newProducts:[]};
 await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})});});
 const base='http://127.0.0.1:'+server.address().port;
 try{
 await p.goto(base+'/admin/statistics');await p.getByRole('button',{name:'已付款订单数量',exact:true}).waitFor();await p.getByRole('button',{name:'已付款订单数量',exact:true}).click();assert.equal(await p.getByRole('button',{name:'已付款订单数量',exact:true}).getAttribute('aria-pressed'),'true');
 await p.locator('.statistics-trend .el-select').click();await p.getByRole('option',{name:'今年按月'}).click();await p.getByRole('button',{name:'已付款订单数量',exact:true}).waitFor({state:'hidden'});assert.equal(await p.getByRole('button',{name:'已付款订单金额',exact:true}).getAttribute('aria-pressed'),'true');
 assert(await p.evaluate(()=>document.body.classList.contains('qivo-admin-theme')));await p.getByRole('button',{name:'账号设置',exact:true}).first().click();await p.getByRole('dialog').waitFor();const color=await p.getByRole('dialog').evaluate(e=>getComputedStyle(e).getPropertyValue('--el-color-primary').trim());assert.equal(color,'#245ae8');await p.keyboard.press('Escape');
 await p.evaluate(()=>{history.pushState({},'', '/');dispatchEvent(new PopStateEvent('popstate'));});await p.locator('.store-header').waitFor();assert.equal(await p.evaluate(()=>document.body.classList.contains('qivo-admin-theme')),false);
 limited=true;await p.goto(base+'/admin/dashboard');await p.locator('.admin-metrics').waitFor();assert.equal(await p.locator('.admin-nav a').count(),2);assert.equal(await p.locator('.dashboard-operations .quick-link').count(),0);assert.equal(await p.locator('.dashboard-orders').count(),0);
 await p.setViewportSize({width:1280,height:600});await p.locator('.admin-content').evaluate(e=>e.scrollTop=e.scrollHeight);assert.equal(await p.evaluate(()=>scrollY),0);assert.equal(await p.locator('.admin-top').evaluate(e=>e.getBoundingClientRect().top),0);
 assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);await fs.writeFile(path.join(out,'新增交互专项检查.json'),JSON.stringify({checks:['金额与数量单位切换','月数据无数量时回到金额','后台弹层继承蓝色主题','离开管理台清除主题作用域','受限角色不出现越权快捷入口和订单区','矮窗口固定顶部且仅内容滚动'],errors,writes},null,2));console.log('Admin theme focused checks passed');
 }finally{await b.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
