// 订单快照、抽屉异常与权限回归；所有业务请求在隔离浏览器内模拟。
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/管理员订单详情验收');await fs.mkdir(out,{recursive:true});
 const html=await fs.readFile(path.join(root,'.local/order-detail-build/index.html'));
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html)});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({channel:'msedge',headless:true}),errors=[],checks=[],requests=[];
 const orders=Array.from({length:8},(_,i)=>({id:i+1,orderNo:'ISOLATED-'+(i+1),createTime:'2026-10-20 10:00:00',payStatus:1,shipStatus:0,totalAmount:'80.00',receiverName:'隔离收件人'}));
 const item={id:101,productId:999,productName:'购买时的随行杯',productImg:'',skuCode:'CUP-W480',skuSnapshotSource:'CURRENT',skuSpecSnapshot:JSON.stringify({text:'暖白 / 480ml'}),unitPrice:'50.00',quantity:2,totalPrice:'100.00'};
 let failure=true,hold=false,release,started;
 let startGate=new Promise(r=>started=r),delayedGate=new Promise(r=>release=r);
 async function contextFor(ship){
  const context=await browser.newContext({viewport:{width:1440,height:900}});
  await context.addInitScript(()=>sessionStorage.setItem('qivo.session.v2.ADMIN',JSON.stringify({token:'isolated-orders',adminId:1,username:'orders'})));
  await context.route('**/api/**',async route=>{
   const req=route.request(),p=new URL(req.url()).pathname;requests.push({p,method:req.method()});
   const send=(data,status=200,code=status)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code,data,msg:status===200?'成功':'隔离测试失败'})});
   if(p==='/api/admin/access')return send({adminId:1,username:'orders',capabilities:{'order:list':true,'order:ship':ship}});
   if(p==='/api/admin/order/list')return send({records:orders,total:orders.length});
   if(p.startsWith('/api/admin/order/detail/')){
    const id=Number(p.split('/').pop());
    if(id===5&&hold){started();await delayedGate}
    if(id===4&&failure)return send(null,503);
    if(id===8)return send(null,404,3001);
    const data={...orders[id-1],items:[{...item}],addressSnapshotSource:'CURRENT',receiverAddressSnapshot:'下单时保存的地址'};
    if(id===2){data.addressSnapshotSource='LEGACY';data.receiverAddressSnapshot=null;data.items=[{...item,id:102,productName:'历史商品',skuSnapshotSource:'LEGACY',skuSpecSnapshot:null,skuCode:null,productImg:null}]}
    if(id===3)data.items=[];
    if(id===5)data.items=[{...item,productName:'不应覆盖新订单的迟到响应'}];
    if(id===7)delete data.items;
    return send(data);
   }
   return send(null,500);
  });return context;
 }
 try{
  const reader=await contextFor(false),page=await reader.newPage();page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000);
  const base=`http://127.0.0.1:${server.address().port}`;await page.goto(base+'/admin/order/list');await page.getByRole('button',{name:'详情',exact:true}).first().waitFor();
  const drawer=page.locator('.el-drawer');
  async function open(id){await page.locator('.el-table__body tbody tr').filter({hasText:'ISOLATED-'+id}).getByRole('button',{name:'详情',exact:true}).click()}
  async function close(){await drawer.getByRole('button',{name:'关闭',exact:true}).click();await drawer.waitFor({state:'hidden'})}
  await open(1);await drawer.getByText('购买时的随行杯',{exact:true}).waitFor();
  assert((await drawer.textContent()).includes('暖白 / 480ml'));assert((await drawer.textContent()).includes('100.00'));assert((await drawer.textContent()).includes('80.00'));
  assert.equal(await drawer.getByRole('button',{name:'填写发货信息',exact:true}).count(),0);
  for(const width of [1280,1440,1920]){
   await page.setViewportSize({width,height:700});const box=await drawer.boundingBox();assert(box.width<=width&&box.x>=0);
   assert(await drawer.getByRole('button',{name:'关闭',exact:true}).isVisible());
   await page.screenshot({path:path.join(out,`01-商品明细-${width}.png`)});
  }
  checks.push('下单快照、成交单价和小计正确，改价后订单金额单独展示；只读账号无发货入口；三种桌面宽度与矮窗口正常');
  await close();await open(2);await drawer.getByText('历史规格未记录',{exact:true}).waitFor();
  await drawer.getByText('历史订单未记录收货快照',{exact:true}).waitFor();await page.screenshot({path:path.join(out,'02-历史快照缺失.png')});
  await close();await open(3);await drawer.getByText('该订单暂无商品明细记录。',{exact:true}).waitFor();
  await close();await open(7);await drawer.getByText('商品明细暂不可用，请稍后重试。',{exact:true}).waitFor();
  checks.push('历史规格及地址缺失、空明细、旧版后端响应分别明确展示，不补造历史数据');
  await close();await open(4);await drawer.getByRole('button',{name:'重新加载',exact:true}).waitFor();
  assert.equal(await drawer.getByText('购买时的随行杯',{exact:true}).count(),0);failure=false;
  await drawer.getByRole('button',{name:'重新加载',exact:true}).click();await drawer.getByText('购买时的随行杯',{exact:true}).waitFor();
  await close();await open(8);await drawer.getByRole('button',{name:'重新加载',exact:true}).waitFor();
  assert.equal(await drawer.getByText('购买时的随行杯',{exact:true}).count(),0);
  checks.push('加载失败及订单不存在时不残留旧明细，主动重试可恢复');
  await close();hold=true;await open(5);await startGate;await drawer.getByLabel('正在加载').waitFor();
  assert.equal(await drawer.getByText('购买时的随行杯',{exact:true}).count(),0);
  await close();await open(2);await drawer.getByText('历史商品',{exact:true}).waitFor();release();
  await page.waitForTimeout(200);assert.equal(await drawer.getByText('不应覆盖新订单的迟到响应',{exact:true}).count(),0);
  checks.push('关闭后快速切换订单，旧请求迟到不会覆盖新订单；加载期间无旧操作');
  const shipper=await contextFor(true),shipPage=await shipper.newPage();shipPage.on('pageerror',e=>errors.push(e.message));
  await shipPage.goto(base+'/admin/order/list');await shipPage.getByRole('button',{name:'详情',exact:true}).first().click();
  const shipDrawer=shipPage.locator('.el-drawer');await shipDrawer.getByRole('button',{name:'填写发货信息',exact:true}).waitFor();
  await shipDrawer.getByRole('button',{name:'填写发货信息',exact:true}).click();await shipPage.getByRole('dialog',{name:'填写发货信息',exact:true}).waitFor();
  assert(!requests.some(r=>r.method!=='GET'));assert(!requests.some(r=>r.p.startsWith('/api/fore/')||r.p.startsWith('/api/admin/product/')),JSON.stringify(requests.filter(r=>r.p.startsWith('/api/fore/')||r.p.startsWith('/api/admin/product/'))));
  checks.push('实际具备发货权限且状态允许时才显示入口；查看快照不请求消费者或当前商品接口，无业务写请求');
  assert.deepEqual(errors,[]);await fs.writeFile(path.join(out,'专项回归记录.json'),JSON.stringify({checks,pageErrors:errors,requests,data:'全部API使用隔离夹具，未修改日常订单'},null,2));
  console.log(JSON.stringify({groups:checks.length,pageErrors:errors.length}));
 }finally{release();await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e.stack);process.exit(1)});
