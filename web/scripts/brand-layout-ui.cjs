// 实际前端构建的品牌排版检查；只读本机公开首页数据，后台接口使用隔离夹具。
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
(async()=>{
 const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/Qivo字标重设计/几何斜切精修落地');await fs.mkdir(out,{recursive:true});
 let limited=false;const errors=[],writes=[],checks=[];
 const server=http.createServer(async(req,res)=>{
  const u=new URL(req.url,'http://localhost');if(req.method!=='GET'){writes.push(u.pathname);res.writeHead(405).end();return}
  if(u.pathname==='/api/fore/index/data'||u.pathname.startsWith('/upload/')){
   const proxy=http.get({hostname:'127.0.0.1',port:8080,path:req.url},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});proxy.on('error',()=>res.writeHead(502).end());return;
  }
  if(u.pathname.startsWith('/api/')){
   let data=[];
   if(u.pathname==='/api/admin/access'){data=require('./fixtures/admin-access.cjs')();if(limited)data.capabilities={'product:list':true};}
   else if(u.pathname==='/api/admin/profile')data={username:'admin',realName:'管理者',avatar:''};
   else if(u.pathname.endsWith('/workbench'))data={todayRevenue:1268,totalOrders:8,paidOrders:6,unpaidOrders:2,onShelf:35,offShelf:5};
   else if(u.pathname.endsWith('/revenue'))data={list:[{date:'2026-10-21',amount:320,count:2},{date:'2026-10-22',amount:580,count:4},{date:'2026-10-23',amount:440,count:3},{date:'2026-10-24',amount:860,count:5},{date:'2026-10-25',amount:720,count:4},{date:'2026-10-26',amount:960,count:6},{date:'2026-10-27',amount:1268,count:6}]};
   else if(u.pathname.endsWith('/order/list')||u.pathname.endsWith('/product/list'))data={records:[],total:0};
   else if(u.pathname.endsWith('/orderStats'))data={totalOrders:8,validOrders:6};
   else if(u.pathname.endsWith('/productTop10'))data={list:[]};
   res.setHeader('Content-Type','application/json');res.end(JSON.stringify({code:200,data}));return;
  }
  res.setHeader('Content-Type','text/html;charset=utf-8');res.end(await fs.readFile(path.join(root,'web/dist/index.html')));
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const context=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1});
  await context.addInitScript(()=>{if(location.pathname.startsWith('/admin'))sessionStorage.setItem('qivo.session.v2.ADMIN',JSON.stringify({token:'brand-layout-only',adminId:1,username:'admin'}))});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  async function ready(url){await page.goto(base+url);await page.locator('.page-loading').waitFor({state:'hidden'}).catch(()=>{});await page.evaluate(()=>Promise.all([...document.images].filter(i=>i.loading!=='lazy').map(i=>i.complete?Promise.resolve():new Promise(r=>{i.onload=r;i.onerror=r}))));await page.waitForTimeout(350)}
  await ready('/');const home=await page.locator('.store-header .brand-logo img').boundingBox();assert.equal(home.width,90);assert.equal(home.height,36);assert(await page.locator('.store-header .brand-logo img').evaluate(i=>i.naturalWidth>0));await page.screenshot({path:path.join(out,'01-首页-几何精修.png')});await page.screenshot({path:path.join(out,'02-首页导航-原尺寸.png'),clip:{x:0,y:0,width:1440,height:76}});checks.push('首页使用正式精修SVG，90×36容器未改变，图片正常加载');
  await ready('/admin/dashboard');const logo=page.locator('.admin-brand img'),caption=page.getByText('商城管理台',{exact:true});const brand=await logo.boundingBox(),text=await caption.boundingBox();assert.equal(brand.width,94);assert.equal(brand.height,38);assert(text.y>=brand.y+brand.height+7);assert(Math.abs(text.x-brand.x)<1);assert.equal(await caption.evaluate(e=>getComputedStyle(e).writingMode),'horizontal-tb');assert.equal(await page.locator('.admin-store-label').count(),0);assert.equal(await page.locator('.admin-brand-label').count(),0);assert.equal(await page.locator('.admin-brand i').count(),0);await page.screenshot({path:path.join(out,'03-后台-新品牌排版.png')});await page.screenshot({path:path.join(out,'04-后台左上角-原尺寸.png'),clip:{x:0,y:0,width:216,height:290}});checks.push('Logo与商城管理台上下同轴，移除重复店铺文字、蓝点和竖线');
  for(const [width,height]of [[1280,600],[1440,900],[1920,1080]]){
   await page.setViewportSize({width,height});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.locator('.admin-content').evaluate(e=>e.scrollTop=e.scrollHeight);assert.equal(await page.evaluate(()=>scrollY),0);assert.equal(await page.locator('.admin-top').evaluate(e=>e.getBoundingClientRect().top),0);assert(await caption.isVisible());assert(await page.getByRole('button',{name:'账号设置',exact:true}).first().isVisible());await page.screenshot({path:path.join(out,`05-后台适配-${width}.png`)});
  }checks.push('1280/1440/1920及600px矮窗口：品牌可见、账号入口可见、仅内容区滚动');
  await page.setViewportSize({width:1440,height:900});await ready('/admin/statistics');await page.locator('.admin-brand .brand-logo').click();await page.waitForURL('**/admin/dashboard');checks.push('后台Logo按当前权限返回管理工作台，不跳转商城');
  limited=true;await ready('/admin/product/list');await page.locator('.admin-brand .brand-logo').click();assert.equal(new URL(page.url()).pathname,'/admin/product/list');assert.equal(await page.locator('.admin-nav a').count(),1);checks.push('受限账号Logo遵循权限落点，不增加业务入口');
  const detail=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:2});await detail.addInitScript(()=>sessionStorage.setItem('qivo.session.v2.ADMIN',JSON.stringify({token:'brand-detail-only',adminId:1,username:'admin'})));await detail.goto(base+'/admin/product/list');await detail.locator('.admin-brand-caption').waitFor();await detail.locator('.admin-brand img').evaluate(i=>i.decode());await detail.screenshot({path:path.join(out,'06-后台品牌区-近景.png'),clip:{x:16,y:16,width:184,height:128}});
  assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);await fs.writeFile(path.join(out,'排版检查.json'),JSON.stringify({checks,homeLogo:home,adminLogo:brand,adminCaption:text,pageErrors:errors,writeRequests:writes,source:'实际生产构建；首页为公开只读数据、后台使用隔离夹具'},null,2));console.log('Brand layout verified: '+checks.length+' groups');
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
