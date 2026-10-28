// 生产构建 + 现有商品快照；全部 API 在浏览器拦截，不连接日常业务服务。
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE || 'playwright');
const http=require('node:http'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/商品浏览改版验收');await fs.mkdir(out,{recursive:true});
const snapshotRoot=path.join(root,'web/test-fixtures/catalog');
const data=JSON.parse(await fs.readFile(path.join(snapshotRoot,'data.json'),'utf8'));
const all=data.products,categories=data.categories,mug=all.find(p=>p.name==='磨砂陶瓷马克杯');
let saved=[],mode='',writes=[],reads=[],bad=[];
const server=http.createServer(async(req,res)=>{try{
const p=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
const product=all.find(pdt=>pdt.mainImage===p);
if(product){res.setHeader('Content-Type','image/webp');res.end(await fs.readFile(path.join(snapshotRoot,product.previewImage)));}
else {res.setHeader('Content-Type','text/html; charset=utf-8');res.end(await fs.readFile(path.join(root,'web/dist/index.html')));}
}catch{res.writeHead(404).end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({channel:'msedge',headless:true});
try {
const context=await browser.newContext({viewport:{width:1440,height:1040},deviceScaleFactor:1});
await context.addInitScript(()=>sessionStorage.setItem('qivo.session.v2.USER',JSON.stringify({token:'isolated-browser-only',userId:999999,username:'isolated'})));
await context.route('**/api/**',async route=>{
 const req=route.request(),url=new URL(req.url()),p=url.pathname.replace(/^\/api/,''),q=url.searchParams,method=req.method();
 const send=(value,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status,data:value,msg:status===200?'':'隔离故障测试'})});
 if(method!=='GET')writes.push({p,body:req.postData()?JSON.parse(req.postData()):null}); else reads.push(p);
 if(p==='/fore/index/data'){if(mode==='home-error')return send(null,500);return send(mode==='home-empty'?{categories:[],hotProducts:[],newProducts:[]}:{categories,hotProducts:[...all].sort((a,b)=>b.sales-a.sales).slice(0,8),newProducts:all.slice(0,8)});}
 if(p==='/fore/cart/list'||p==='/fore/user/address')return send([]);
 if(p==='/fore/product/collect'&&method==='GET')return send(saved);
 if(p==='/fore/product/collect'&&method==='POST'){if(mode==='save-error')return send(null,500);saved.push(req.postDataJSON().productId);return send(null);}
 if(p.startsWith('/fore/product/collect/')&&method==='DELETE'){saved=saved.filter(id=>id!==Number(p.split('/').pop()));return send(null);}
 if(p==='/fore/product/list'){
  if(q.get('pageSize')==='12'){
   if(mode==='error')return send(null,500);
   if(mode==='loading')await new Promise(r=>setTimeout(r,900));
   if(q.get('keyword')==='慢请求')await new Promise(r=>setTimeout(r,1000));
  }
  let rows=[...all];const categoryId=Number(q.get('categoryId'));
  if(categoryId)rows=rows.filter(p=>p.categoryId===categoryId||categories.find(c=>c.id===p.categoryId)?.parentId===categoryId);
  if(q.get('keyword'))rows=rows.filter(p=>p.name.includes(q.get('keyword')));
  if(mode==='empty'&&q.get('pageSize')==='12')rows=[];
  if(q.get('sort')==='price_asc')rows.sort((a,b)=>a.price-b.price||a.id-b.id);
  if(q.get('sort')==='price_desc')rows.sort((a,b)=>b.price-a.price||b.id-a.id);
  if(q.get('sort')==='sales')rows.sort((a,b)=>b.sales-a.sales);
  const page=Number(q.get('page')||1),size=Number(q.get('pageSize')||12);
  return send({records:rows.slice((page-1)*size,page*size),total:rows.length});
 }
 if(p.startsWith('/fore/product/detail/')){
  if(mode==='detail-error')return send(null,500);
  const product=all.find(v=>v.id===Number(p.split('/').pop()));
  if(!product)return send(null,404);
  return send({product,skus:product.variants,specDefinitions:product.definitions,collected:saved.includes(product.id)});
 }
 if(p.startsWith('/fore/comment/list/'))return mode==='review-error'?send(null,500):send({records:[],total:0});
 if(p==='/fore/cart/add')return send(null);
 bad.push(p);return send(null,404);
});
const page=await context.newPage(),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
const ready=async()=>{await page.locator('.page-loading').waitFor({state:'hidden'});await page.evaluate(()=>Promise.all([...document.images].map(i=>{i.loading='eager';return i.complete?Promise.resolve():new Promise(r=>{i.onload=r;i.onerror=r;});})));};
await page.goto(base+'/');await page.locator('.scene-photo img').waitFor();await ready();
for(const width of [1280,1440,1920]){
 await page.setViewportSize({width,height:1040});await ready();
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert(await page.locator('.scene-photo img').evaluate(i=>i.naturalWidth>0));
 assert.equal(await page.locator('.curated-section .product-card').count(),4);
 assert.equal(await page.locator('.arrival-list > a').count(),4);
 assert.equal(await page.locator('.store-footer a').count(),0);
 await page.screenshot({path:path.join(out,'首页场景-'+width+'.png'),fullPage:true});
}
const related=await page.locator('.scene-products a').first().getAttribute('href');
await page.locator('.scene-products a').first().click();await page.locator('.detail-info h1').waitFor();assert(new URL(page.url()).pathname===related);
mode='home-empty';await page.goto(base+'/');await page.getByText('商品正在准备中',{exact:true}).waitFor();assert.equal(await page.locator('.scene-products').count(),0);
mode='home-error';await page.reload();await page.getByRole('button',{name:'重新加载',exact:true}).waitFor();mode='';await page.getByRole('button',{name:'重新加载',exact:true}).click();await page.locator('.scene-products').waitFor();
await page.goto(base+'/product/list?sort=price_asc');await page.locator('.product-card').first().waitFor();await ready();
for(const width of [1280,1440,1920]){
 await page.setViewportSize({width,height:1040});await ready();
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert.equal(await page.locator('.product-card').count(),12);
 await page.screenshot({path:path.join(out,'商品列表-'+width+'.png'),fullPage:true});
}
assert.equal(await page.locator('.store-footer a').count(),0); // only brand link; no duplicate navigation
await page.getByRole('group',{name:'商品子类',exact:true}).getByRole('button',{name:'杯具',exact:true}).click();
await page.waitForFunction(()=>document.querySelectorAll('.product-card').length===7);
assert.equal(new URL(page.url()).searchParams.get('page'),null);
await page.goBack();await page.waitForFunction(()=>document.querySelectorAll('.product-card').length===12);
await page.locator('.el-pagination .el-pager li').filter({hasText:/^2$/}).click();await page.waitForURL('**page=2');
await page.reload();await ready();assert.equal(await page.locator('.el-pager .is-active').innerText(),'2');
const saveButton=page.locator('.product-save').first();await saveButton.click();await page.waitForFunction(()=>document.querySelector('.product-save')?.getAttribute('aria-pressed')==='true');assert.equal(saved.length,1);
mode='save-error';await page.locator('.product-save').nth(1).click();await page.waitForTimeout(150);assert.equal(await page.locator('.product-save[aria-pressed=true]').count(),1);mode='';
await page.goto(base+'/product/'+mug.id);await page.locator('.detail-info h1').waitFor();await ready();
assert(await page.getByRole('button',{name:'立即购买',exact:true}).isDisabled());
await page.locator('.el-radio-button').filter({has:page.getByRole('radio',{name:/400/})}).click();assert(await page.getByRole('button',{name:'立即购买',exact:true}).isEnabled());
assert((await page.locator('.detail-price').innerText()).includes('59.00'));
await page.getByRole('heading',{name:mug.name,exact:true}).click();
for(const width of [1280,1440,1920]){
 await page.setViewportSize({width,height:1040});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.screenshot({path:path.join(out,'商品详情-'+width+'.png'),fullPage:true});
}
await page.getByRole('button',{name:/放大查看/}).click();await page.locator('.el-image-viewer__wrapper').waitFor();await page.keyboard.press('Escape');
await page.getByRole('button',{name:'加入购物车',exact:true}).click();await page.waitForTimeout(100);
assert(writes.some(w=>w.p==='/fore/cart/add'&&w.body.skuId===mug.variants.find(s=>s.price===59).id));
await page.getByRole('button',{name:'立即购买',exact:true}).click();await page.waitForURL('**/order/confirm?**');assert(new URL(page.url()).searchParams.has('skuId'));
mode='empty';await page.goto(base+'/product/list');await page.getByText('没有找到相关商品',{exact:true}).waitFor();await page.screenshot({path:path.join(out,'商品列表-空状态.png'),fullPage:true});
mode='error';await page.reload();await page.getByRole('button',{name:'重新加载',exact:true}).waitFor();await page.screenshot({path:path.join(out,'商品列表-失败状态.png'),fullPage:true});
mode='';await page.getByRole('button',{name:'重新加载',exact:true}).click();await page.locator('.product-card').first().waitFor();
mode='review-error';await page.goto(base+'/product/'+mug.id);await page.getByRole('button',{name:'重新加载',exact:true}).waitFor();mode='';await page.getByRole('button',{name:'重新加载',exact:true}).click();await page.locator('.review-empty').waitFor();
const empty=all.find(p=>p.totalStock===0);await page.goto(base+'/product/'+empty.id);await page.locator('.detail-info h1').waitFor();assert(await page.getByRole('button',{name:'立即购买',exact:true}).isDisabled());
// Loading, stale query and anonymous identity checks.
mode='loading';await page.goto(base+'/product/list');await page.locator('.page-loading').waitFor();await page.screenshot({path:path.join(out,'商品列表-加载状态.png'),fullPage:true});await page.locator('.product-card').first().waitFor();mode='';
await page.locator('.header-search input').fill('慢请求');await page.locator('.header-search input').press('Enter');
await page.waitForURL('**keyword=**');await page.locator('.header-search input').fill('杯');await page.locator('.header-search input').press('Enter');
await page.waitForFunction(()=>document.querySelector('.catalog-page-heading h1')?.textContent.includes('杯'));await page.locator('.product-card').first().waitFor();await page.waitForTimeout(1100);
assert((await page.locator('.product-card h3').allTextContents()).every(name=>name.includes('杯')));
// Single specification quick-add and anonymous actions.
await page.goto(base+'/product/list?sort=price_asc');await page.locator('.product-card').first().waitFor();
await page.locator('.product-card').first().getByRole('button',{name:'加入购物车',exact:true}).click();await page.waitForTimeout(100);
assert(writes.some(w=>w.p==='/fore/cart/add'&&w.body.skuId===all.find(p=>p.name.includes('AirBook')).defaultSkuId));
await page.evaluate(()=>{sessionStorage.removeItem('qivo.session.v2.USER');window.dispatchEvent(new Event('qivo-session-changed'));});
assert.equal(await page.locator('.product-save[aria-pressed=true]').count(),0);
const before=writes.length;await page.locator('.product-save').first().click();await page.waitForURL('**/login?**');assert.equal(writes.length,before);
assert.deepEqual(errors,[]);assert.deepEqual(bad,[]);
await fs.writeFile(path.join(out,'专项检查记录.json'),JSON.stringify({widths:[1280,1440,1920],pageErrors:errors,writes,unhandled:bad,checks:['真实数据快照隔离接口','首页三种宽度与图片加载','首页真实商品入口','首页空和失败重试','纯字标和版权页脚','分类与分页刷新恢复','收藏与失败保留状态','多规格价格和真实SKU加购','购买跳转参数','缺货禁止购买','图片放大及Escape','空与失败重试','评价失败重试','加载状态','快速搜索旧请求不覆盖','单规格快捷加购','匿名收藏进入登录且不写入']},null,2));
console.log(JSON.stringify({errors,writes:writes.length,unhandled:bad}));
}finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
