// 三项改造端到端交互回归。所有业务API为隔离内存模型，不连接日常服务。
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require(process.env.QIVO_PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/设计原型/三项改造验收');await fs.mkdir(out,{recursive:true});
 const html=await fs.readFile(path.join(root,'.local/operations-build/index.html'));
 const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(html)});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({channel:'msedge',headless:true}),checks=[],errors=[],events=[],keys=[];
 const roles=[{id:1,name:'超级管理员',code:'super_admin'},{id:2,name:'商品管理员',code:'product_admin'}];
 const admins=[{id:1,username:'admin',realName:'管理者',status:1,roles:[roles[0]],createTime:'2026-10-25 10:00:00'},{id:2,username:'operator',realName:'同事',status:1,roles:[roles[1]]}];
 const order={id:701,orderNo:'TEST-701',userId:1,totalAmount:35,payStatus:1,shipStatus:0,createTime:'2026-10-25 10:00:00',addressSnapshotSource:'LEGACY'};
 let applications=[],attempts=[],applyUnknown=true,refundFails=true;
 const all=require('./fixtures/admin-access.cjs')().capabilities;
 async function context(actor){
  const ctx=await browser.newContext({viewport:{width:1440,height:900}});
  await ctx.addInitScript(actor=>sessionStorage.setItem('qivo.session.v2.'+(actor==='buyer'?'USER':'ADMIN'),JSON.stringify({token:'isolated-'+actor,username:actor,adminId:actor==='reader'?2:1,userId:actor==='buyer'?1:undefined})),actor);
  await ctx.route('**/api/**',async route=>{
   const req=route.request(),url=new URL(req.url()),p=url.pathname.replace('/api',''),method=req.method();events.push({p,method,actor});
   const send=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify({code:status,data,msg:status===200?'成功':'隔离模拟网络失败'})});
   if(p==='/admin/access')return send({adminId:actor==='reader'?2:1,username:actor,capabilities:actor==='reader'?{'admin:list':true}:all});
   if(p==='/admin/role/list')return send(roles);
   if(p==='/admin/admin/list'){let rows=admins.filter(r=>(!url.searchParams.get('keyword')||r.username.includes(url.searchParams.get('keyword')))&&(!url.searchParams.has('status')||r.status===Number(url.searchParams.get('status'))));return send({records:rows,total:rows.length})}
   if(p==='/admin/admin/add'){const b=req.postDataJSON();admins.push({id:3,username:b.username,realName:b.realName,status:1,roles:[roles.find(r=>r.id===b.roleId)]});return send(null)}
   if(/^\/admin\/admin\/\d+\/status$/.test(p)){admins.find(r=>r.id===Number(p.split('/')[3])).status=req.postDataJSON().status;return send(null)}
   if(/^\/admin\/admin\/\d+\/password$/.test(p))return send(null);
   if(/^\/admin\/admin\/\d+$/.test(p)){const row=admins.find(r=>r.id===Number(p.split('/')[3])),b=req.postDataJSON();row.realName=b.realName;if(b.roleId)row.roles=[roles.find(r=>r.id===b.roleId)];return send(null)}
   if(p==='/fore/order/list'||p==='/admin/order/list')return send({records:[order],total:1});
   if(p==='/fore/order/detail/701')return send({order,items:[],comments:[]});
   if(p==='/admin/order/detail/701')return send({...order,items:[]});
   if(p==='/fore/cart/list')return send([]);
   if(p==='/fore/after-sale/order/701')return send(applications);
   if(p==='/fore/after-sale'){
    const key=req.headers()['idempotency-key'];keys.push(key);const b=req.postDataJSON();let row=applications.find(x=>x.requestKey===key);
    if(!row){row={id:71,orderId:701,orderNo:order.orderNo,status:'REQUESTED',amount:35,reason:b.reason,requestKey:key,createTime:'2026-10-25 10:05:00'};applications.push(row)}
    if(applyUnknown){applyUnknown=false;return send(null,503)}return send(row);
   }
   if(p==='/admin/after-sale/list'){const status=url.searchParams.get('status');const rows=applications.filter(r=>!status||r.status===status);return send({records:rows,total:rows.length})}
   if(p==='/admin/after-sale/71/attempts')return send(attempts);
   if(p==='/admin/after-sale/71/review'){
    const b=req.postDataJSON(),row=applications[0];row.reviewNote=b.note;row.reviewerId=1;
    if(!b.approve)row.status='REJECTED';else{const failed=refundFails;refundFails=false;row.status=failed?'REFUND_FAILED':'REFUNDED';attempts.push({id:attempts.length+1,attemptNo:attempts.length+1,status:failed?'FAILED':'SUCCEEDED',message:failed?'隔离通道失败':'模拟退款成功，未发生真实资金退回',operatorId:1});if(!failed)order.payStatus=2}
    return send(row);
   }
   if(p==='/admin/statistics/workbench')return send({todayRevenue:0,totalOrders:0,paidOrders:0,unpaidOrders:0,onShelf:0,offShelf:0});
   if(p==='/admin/statistics/revenue')return send({list:[]});
   return send([]);
  });return ctx;
 }
 const base=`http://127.0.0.1:${server.address().port}`;
 try{
  const ctx=await context('root'),admin=await ctx.newPage();admin.setDefaultTimeout(10000);admin.on('pageerror',e=>errors.push(e.message));
  await admin.goto(base+'/admin/admin/list');await admin.getByRole('button',{name:'新增管理员',exact:true}).waitFor();await admin.getByText('operator',{exact:true}).waitFor();
  await admin.getByText('超级管理员',{exact:true}).waitFor();assert.equal(await admin.locator('.admin-nav a').count(),9);assert.equal(await admin.locator('.admin-brand').getByText('管理台',{exact:true}).count(),0);
  const current=admin.locator('.el-table__body tbody tr').filter({hasText:'当前账号'});assert.equal(await current.getByRole('button',{name:'停用',exact:true}).count(),0);assert.equal(await current.getByRole('button',{name:'重设密码',exact:true}).count(),0);
  await admin.getByRole('button',{name:'新增管理员',exact:true}).click();let drawer=admin.locator('.el-drawer');let inputs=drawer.locator('input');
  await inputs.nth(0).fill('new_manager');await inputs.nth(1).fill('新同事');await drawer.locator('.el-select').click();await admin.getByRole('option',{name:'商品管理员',exact:true}).click();
  await drawer.locator('input[type=password]').nth(0).fill('password123');await drawer.locator('input[type=password]').nth(1).fill('password123');await drawer.getByRole('button',{name:'保存账号',exact:true}).click();await drawer.waitFor({state:'hidden'});await admin.getByText('new_manager',{exact:true}).waitFor();
  checks.push('管理员新增并绑定角色；当前账号不显示停用或重设密码；九项菜单按职责组织');
  const row=admin.locator('.el-table__body tbody tr').filter({hasText:'new_manager'});await row.getByRole('button',{name:'停用',exact:true}).click();await admin.getByRole('button',{name:'确定',exact:true}).click();await row.getByText('已停用',{exact:true}).waitFor();
  await admin.locator('.el-message-box').waitFor({state:'hidden'});await admin.waitForFunction(()=>document.querySelectorAll('.el-message').length===0);await admin.screenshot({path:path.join(out,'01-管理员账号.png'),fullPage:true});checks.push('停用需确认，操作成功后刷新真实列表状态');
  const readCtx=await context('reader'),reader=await readCtx.newPage();await reader.goto(base+'/admin/admin/list');await reader.getByText('new_manager',{exact:true}).waitFor();assert.equal(await reader.getByRole('button',{name:'新增管理员',exact:true}).count(),0);assert.equal(await reader.getByRole('button',{name:'编辑',exact:true}).count(),0);checks.push('账号只读权限可查看角色与状态，不显示账号写入口');
  await admin.getByRole('button',{name:'个人密码设置',exact:true}).click();await admin.getByRole('dialog',{name:'修改我的密码',exact:true}).waitFor();await admin.getByRole('button',{name:'取消',exact:true}).click();checks.push('个人改密有独立入口，关闭不改动账号');
  const buyerCtx=await context('buyer'),buyer=await buyerCtx.newPage();buyer.setDefaultTimeout(10000);buyer.on('pageerror',e=>errors.push(e.message));
  await buyer.goto(base+'/order/list');await buyer.getByRole('button',{name:'售后申请与记录',exact:true}).click();let buyerDrawer=buyer.locator('.el-drawer');await buyerDrawer.getByText('暂无售后申请',{exact:true}).waitFor();
  await buyerDrawer.locator('textarea').fill('不再需要这件商品');await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).click();await buyerDrawer.getByRole('button',{name:'重试原申请',exact:true}).waitFor();assert(await buyerDrawer.locator('textarea').isDisabled());
  await buyerDrawer.getByRole('button',{name:'重试原申请',exact:true}).click();await buyerDrawer.getByText('待审核',{exact:true}).waitFor();assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);assert.equal(applications.length,1);await buyer.screenshot({path:path.join(out,'02-消费者售后申请.png')});checks.push('申请结果未知时保留原内容与幂等键；重试不新增第二张申请，买家看到待审核');
  await admin.goto(base+'/admin/after-sale/list');await admin.getByRole('button',{name:'查看处理',exact:true}).click();drawer=admin.locator('.el-drawer');await drawer.getByText('尚无退款尝试',{exact:true}).waitFor();
  await drawer.getByRole('button',{name:'拒绝申请',exact:true}).click();await admin.getByText('请填写拒绝原因',{exact:true}).waitFor();assert.equal(applications[0].status,'REQUESTED');
  await drawer.getByRole('button',{name:'批准并模拟退款',exact:true}).click();await admin.getByRole('button',{name:'确定',exact:true}).click();await drawer.getByRole('button',{name:'重试模拟退款',exact:true}).waitFor();assert.equal(order.payStatus,1);checks.push('拒绝必须填写原因；模拟通道失败保持已付款订单，保留失败尝试并提供重试');
  await drawer.getByRole('button',{name:'重试模拟退款',exact:true}).click();await admin.getByRole('button',{name:'确定',exact:true}).click();await drawer.getByText('已模拟退款',{exact:true}).waitFor();assert.equal(attempts.length,2);assert.equal(await drawer.getByRole('button',{name:'重试模拟退款',exact:true}).count(),0);await admin.locator('.el-message-box').waitFor({state:'hidden'});await admin.waitForFunction(()=>document.querySelectorAll('.el-message').length===0);await admin.screenshot({path:path.join(out,'03-售后审核与退款记录.png')});
  await buyerDrawer.getByRole('button',{name:'刷新申请结果',exact:true}).click();await buyerDrawer.getByText('已模拟退款',{exact:true}).waitFor();assert.equal(await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).count(),0);checks.push('原申请重试成功后显示两次审计记录，买家可见完成结果，不能重复申请或重复批准');
  for(const width of [1280,1440,1920]){await admin.setViewportSize({width,height:650});const side=await admin.locator('.admin-side').boundingBox(),top=await admin.locator('.admin-top').boundingBox();assert.equal(side.y,0);assert.equal(top.y,0);await admin.locator('.el-drawer__body').evaluate(el=>el.scrollTo(0,el.scrollHeight));assert(await drawer.getByRole('button',{name:'关闭',exact:true}).isVisible());}
  checks.push('三种桌面宽度与矮窗口下导航固定、抽屉内部滚动且底部操作可见');
  assert.deepEqual(errors,[]);await fs.writeFile(path.join(out,'专项回归记录.json'),JSON.stringify({checks,events,pageErrors:errors,data:'全部业务接口隔离模拟'},null,2));console.log(JSON.stringify({groups:checks.length,pageErrors:errors.length}));
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e.stack);process.exit(1)});
