// 真实隔离后端与数据库。仅注入一次网关503（真实请求已提交），不模拟业务API响应。
const fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),net=require('node:net'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {chromium}=require('C:/Users/27362/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
 const input=JSON.parse(await fs.readFile(process.argv[2],'utf8')),backend=new URL(input.backend),root=path.resolve(__dirname,'../..'),out=path.join(input.run,'页面证据');
 assert.equal(backend.hostname,'127.0.0.1');assert.notEqual(backend.port,'8080');await fs.mkdir(out,{recursive:true});
 const frontend=path.join(root,'.local/operations-build'),checks=[],errors=[],events=[],tokens=[],suffix=crypto.randomBytes(3).toString('hex');
 let cutApplyResponse=true,cutCount=0;
 const server=http.createServer(async(req,res)=>{
  if(req.url.startsWith('/api/')||req.url.startsWith('/upload/')){
   const event={method:req.method,path:req.url.split('?')[0]};events.push(event);
   const upstream=http.request(new URL(req.url,input.backend),{method:req.method,headers:{...req.headers,host:backend.host,...(req.headers.origin===origin?{origin:backend.origin}:{})}},r=>{
    event.status=r.statusCode;
    if(req.url==='/api/fore/after-sale'&&req.method==='POST'&&cutApplyResponse&&r.statusCode===200){cutApplyResponse=false;cutCount++;r.resume();r.on('end',()=>{res.writeHead(503,{'Content-Type':'application/json'});res.end(JSON.stringify({code:503,msg:'隔离代理故障注入：原请求已提交，响应未确认'}))});return}
    res.writeHead(r.statusCode,r.headers);r.pipe(res);
   });upstream.on('error',()=>{res.statusCode=502;res.end()});req.pipe(upstream);return;
  }
  const p=path.resolve(frontend,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
  if(p.startsWith(frontend+path.sep)&&(await fs.stat(p).catch(()=>null))?.isFile()){
   res.setHeader('Content-Type',({'.png':'image/png','.webp':'image/webp','.svg':'image/svg+xml'}[path.extname(p)])||'text/html; charset=utf-8');res.end(await fs.readFile(p));return;
  }
  res.setHeader('Content-Type','text/html; charset=utf-8');res.end(await fs.readFile(path.join(frontend,'index.html')));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const origin=`http://127.0.0.1:${server.address().port}`;
 async function api(p,token,method='GET',body,extra={}){
  const r=await fetch(origin+'/api'+p,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...extra},body:body===undefined?undefined:JSON.stringify(body)});
  const raw=await r.json();return {status:r.status,data:Object.hasOwn(raw,'data')?raw.data:raw,msg:raw.msg};
 }
 async function ok(p,token,method='GET',body,extra){const r=await api(p,token,method,body,extra);assert.equal(r.status,200,method+' '+p+' '+r.msg);return r.data}
 function redisGet(key){return new Promise((resolve,reject)=>{
  const sock=net.createConnection({host:'127.0.0.1',port:input.redisPort});let data=Buffer.alloc(0);
  const resp=arr=>'*'+arr.length+'\r\n'+arr.map(v=>'$'+Buffer.byteLength(v)+'\r\n'+v+'\r\n').join('');
  sock.setTimeout(5000,()=>sock.destroy(new Error('独立Redis读取超时')));sock.on('error',reject);
  sock.on('connect',()=>sock.write(resp(['AUTH',input.redisPassword])+resp(['GET',key])));
  sock.on('data',chunk=>{data=Buffer.concat([data,chunk]);const first=data.indexOf('\r\n');if(first<0)return;const offset=first+2,end=data.indexOf('\r\n',offset);if(end<0)return;const length=Number(data.subarray(offset+1,end).toString());if(length<0){sock.end();resolve(null);return}if(data.length>=end+2+length+2){sock.end();resolve(data.subarray(end+2,end+2+length).toString())}});
 })}
 async function login(name,password,admin=true){const cap=await ok('/common/captcha/get');const code=JSON.parse(await redisGet('captcha:'+cap.captchaKey));const r=await ok('/common/auth/'+(admin?'adminLogin':'login'),null,'POST',{username:name,password,captchaKey:cap.captchaKey,captchaCode:code});tokens.push(r.token);return r}
 const browser=await chromium.launch({channel:'msedge',headless:true}),admin=input.admin;tokens.push(admin.token);
 async function pageFor(session,domain='ADMIN'){
  const context=await browser.newContext({viewport:{width:1440,height:900}});context.setDefaultTimeout(15000);
  await context.addInitScript(({session,domain})=>sessionStorage.setItem('qivo.session.v2.'+domain,JSON.stringify(session)),{session,domain});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));return page;
 }
 async function quiet(page){await page.locator('.el-message-box').waitFor({state:'hidden'});await page.waitForFunction(()=>document.querySelectorAll('.el-message').length===0)}
 try{
  const perms=await ok('/admin/permission/tree',admin.token),permission=perms.find(x=>x.code==='admin:list');assert(permission);
  const roleName='联调只读角色'+suffix;
  await ok('/admin/role/add',admin.token,'POST',{name:roleName,code:'e2e_'+suffix,description:'仅独立副本测试'});
  const roles=await ok('/admin/role/list',admin.token),role=roles.find(x=>x.name===roleName);assert(role);
  await ok('/admin/role/permission',admin.token,'POST',{roleId:role.id,permissionIds:[permission.id],version:role.version});
  const rootPage=await pageFor(admin);await rootPage.goto(origin+'/admin/admin/list');await rootPage.locator('.admin-nav a').nth(8).waitFor();
  assert.equal(await rootPage.locator('.admin-nav a').count(),9);assert.equal(await rootPage.getByText('查看商城',{exact:true}).count(),0);
  await rootPage.getByRole('button',{name:'新增管理员',exact:true}).click();let drawer=rootPage.locator('.el-drawer');
  const username='e2e_admin_'+suffix,password='testAccount123',newPassword='changedAccount123';
  await drawer.locator('input').nth(0).fill(username);await drawer.locator('input').nth(1).fill('隔离账号');await drawer.locator('.el-select').click();await rootPage.getByRole('option',{name:roleName,exact:true}).click();
  await drawer.locator('input[type=password]').nth(0).fill(password);await drawer.locator('input[type=password]').nth(1).fill(password);const createResponse=rootPage.waitForResponse(r=>r.url().endsWith('/api/admin/admin/add'),{timeout:5000});await drawer.getByRole('button',{name:'保存账号',exact:true}).click();const created=await createResponse;assert.equal(created.status(),200,'新增账号HTTP结果');await drawer.waitFor({state:'hidden'});await rootPage.getByText(username,{exact:true}).waitFor();
  const accounts=await ok('/admin/admin/list?keyword='+username,admin.token),account=accounts.records.find(a=>a.username===username);assert.equal(account.roles[0].id,role.id);assert(!Object.hasOwn(account,'password'));
  let limited=await login(username,password);const row=rootPage.locator('.el-table__body tbody tr').filter({hasText:username});
  await row.getByRole('button',{name:'编辑',exact:true}).click();drawer=rootPage.locator('.el-drawer');await drawer.locator('input').nth(1).fill('隔离账号已编辑');await drawer.getByRole('button',{name:'保存账号',exact:true}).click();await drawer.waitFor({state:'hidden'});await row.getByText('隔离账号已编辑',{exact:true}).waitFor();
  checks.push('真实UI创建管理员与角色原子绑定、编辑姓名、列表响应不含密码');
  await row.getByRole('button',{name:'停用',exact:true}).click();await rootPage.getByRole('button',{name:'确定',exact:true}).click();await row.getByText('已停用',{exact:true}).waitFor();assert.equal((await api('/admin/profile',limited.token)).status,401);
  await row.getByRole('button',{name:'启用',exact:true}).click();await rootPage.getByRole('button',{name:'确定',exact:true}).click();await row.getByText('已启用',{exact:true}).waitFor();assert.equal((await api('/admin/profile',limited.token)).status,401);limited=await login(username,password);
  await row.getByRole('button',{name:'重设密码',exact:true}).click();drawer=rootPage.locator('.el-drawer');await drawer.locator('input[type=password]').nth(0).fill(newPassword);await drawer.locator('input[type=password]').nth(1).fill(newPassword);await drawer.getByRole('button',{name:'保存账号',exact:true}).click();await drawer.waitFor({state:'hidden'});assert.equal((await api('/admin/profile',limited.token)).status,401);limited=await login(username,newPassword);
  checks.push('真实Redis会话：停用、重启用及重设密码后旧令牌不能复活；新密码可登录');
  const limitedPage=await pageFor(limited);await limitedPage.goto(origin+'/admin');await limitedPage.waitForURL(u=>u.pathname==='/admin/admin/list');await limitedPage.getByText(username,{exact:true}).waitFor();assert.equal(await limitedPage.locator('.admin-nav a').count(),1);assert.equal(await limitedPage.getByRole('button',{name:'新增管理员',exact:true}).count(),0);
  assert.equal((await api('/admin/admin/'+account.id+'/status',limited.token,'PUT',{status:0})).status,403);
  assert.equal((await api('/admin/admin/'+admin.adminId+'/status',admin.token,'PUT',{status:0})).status,409);
  assert.equal((await api('/admin/admin/'+admin.adminId,admin.token,'PUT',{realName:admin.realName,roleId:role.id})).status,409);
  await quiet(rootPage);await rootPage.screenshot({path:path.join(out,'01-真实管理员账号.png'),fullPage:true});checks.push('只读角色菜单、登录落点与写接口403一致；当前管理员不能停用或降权自己');
  const buyerName='e2e_buyer_'+suffix;await ok('/fore/user/register',null,'POST',{username:buyerName,password:'testBuyer123'});const buyer=await login(buyerName,'testBuyer123',false);
  await ok('/fore/user/address',buyer.token,'POST',{receiverName:'隔离买家',receiverPhone:'13800000000',province:'测试省',city:'测试市',district:'测试区',detail:'仅隔离库使用',isDefault:1});const addresses=await ok('/fore/user/address',buyer.token);const address=addresses[0];assert(address);
  const catalog=await ok('/admin/product/list?page=1&pageSize=100',admin.token);let product,sku;
  for(const p of catalog.records.filter(p=>p.status===1)){const detail=await ok('/admin/product/detail/'+p.id,admin.token);const s=detail.skus.find(x=>x.status==='ENABLED'&&x.stock>=4);if(s){product=detail;sku=s;break}}
  assert(sku,'副本需有可售规格');const initialStock=sku.stock,initialSales=product.sales;
  async function purchase(qty,pay=true){const created=await ok('/fore/order/buy-now',buyer.token,'POST',{skuId:sku.id,addressId:address.id,quantity:qty,expectedTotal:(Number(sku.price)*qty).toFixed(2),remark:'真实隔离联调'},{'Idempotency-Key':crypto.randomUUID()});if(pay)await ok('/fore/order/pay',buyer.token,'POST',{orderId:created.orderId});return created}
  const first=await purchase(2);let after=await ok('/admin/product/detail/'+product.id,admin.token);assert.equal(after.skus.find(x=>x.id===sku.id).stock,initialStock-2);
  const buyerPage=await pageFor(buyer,'USER');await buyerPage.goto(origin+'/order/list');let orderCard=buyerPage.locator('.customer-order').filter({hasText:first.orderNo});await orderCard.getByRole('button',{name:'售后申请与记录',exact:true}).click();let buyerDrawer=buyerPage.locator('.el-drawer');await buyerDrawer.getByText('暂无售后申请',{exact:true}).waitFor();await buyerDrawer.locator('textarea').fill('隔离联调：不再需要商品');await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).click();await buyerDrawer.getByRole('button',{name:'重试原申请',exact:true}).waitFor();await buyerDrawer.getByRole('button',{name:'重试原申请',exact:true}).click();await buyerDrawer.getByText('待审核',{exact:true}).waitFor();
  const history=await ok('/fore/after-sale/order/'+first.orderId,buyer.token);assert.equal(history.length,1);assert.equal(cutCount,1);const application=history[0];
  assert.equal((await api('/admin/order/ship/'+first.orderId,admin.token,'PUT',{trackingNo:'ISOLATED-TRACK'})).status,409);
  checks.push('真实注册、地址、购买及模拟付款；截断已提交响应后原键重试只有一张申请，申请中发货被409拦截');
  await rootPage.goto(origin+'/admin/after-sale/list');let saleRow=rootPage.locator('.el-table__body tbody tr').filter({hasText:first.orderNo});await saleRow.getByRole('button',{name:'查看处理',exact:true}).click();drawer=rootPage.locator('.el-drawer');await drawer.getByText('尚无退款尝试',{exact:true}).waitFor();await drawer.getByRole('button',{name:'批准并模拟退款',exact:true}).click();await rootPage.getByRole('button',{name:'确定',exact:true}).click();await drawer.getByText('已模拟退款',{exact:true}).waitFor();
  const orderDetail=await ok('/admin/order/detail/'+first.orderId,admin.token);assert.equal(orderDetail.payStatus,2);after=await ok('/admin/product/detail/'+product.id,admin.token);assert.equal(after.skus.find(x=>x.id===sku.id).stock,initialStock);assert.equal(after.sales,initialSales);
  await ok('/admin/after-sale/'+application.id+'/review',admin.token,'POST',{approve:true,note:'重复批准应返回原结果'});assert.equal((await ok('/admin/after-sale/'+application.id+'/attempts',admin.token)).length,1);
  const movements=await ok('/admin/product/'+product.id+'/stock-movements?page=1&pageSize=100',admin.token);assert.equal(movements.records.filter(x=>x.orderId===first.orderId&&x.kind==='REFUND_RELEASE').length,1);
  await buyerDrawer.getByRole('button',{name:'刷新申请结果',exact:true}).click();await buyerDrawer.getByText('已模拟退款',{exact:true}).waitFor();assert.equal(await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).count(),0);
  await quiet(rootPage);await rootPage.screenshot({path:path.join(out,'02-真实退款审核与记录.png')});await buyerPage.screenshot({path:path.join(out,'03-真实买家售后结果.png')});checks.push('真实UI批准退款，订单已退款、库存销量恢复、库存流水及成功尝试各一次，买家看到完成结果');
  const second=await purchase(1);await buyerPage.reload();orderCard=buyerPage.locator('.customer-order').filter({hasText:second.orderNo});await orderCard.getByRole('button',{name:'售后申请与记录',exact:true}).click();buyerDrawer=buyerPage.locator('.el-drawer');await buyerDrawer.locator('textarea').fill('隔离联调：验证拒绝');await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).click();await buyerDrawer.getByText('待审核',{exact:true}).waitFor();
  let secondHistory=await ok('/fore/after-sale/order/'+second.orderId,buyer.token);await rootPage.reload();saleRow=rootPage.locator('.el-table__body tbody tr').filter({hasText:second.orderNo});await saleRow.getByRole('button',{name:'查看处理',exact:true}).click();drawer=rootPage.locator('.el-drawer');await drawer.locator('textarea').fill('隔离联调：拒绝后允许重新申请');await drawer.getByRole('button',{name:'拒绝申请',exact:true}).click();await rootPage.getByRole('button',{name:'确定',exact:true}).click();await drawer.getByText('已拒绝',{exact:true}).waitFor();
  await buyerDrawer.getByRole('button',{name:'刷新申请结果',exact:true}).click();await buyerDrawer.getByText('已拒绝',{exact:true}).waitFor();await buyerDrawer.locator('textarea').fill('隔离联调：重新申请');await buyerDrawer.getByRole('button',{name:'申请整单模拟退款',exact:true}).click();await buyerDrawer.getByText('待审核',{exact:true}).waitFor();secondHistory=await ok('/fore/after-sale/order/'+second.orderId,buyer.token);assert.equal(secondHistory.length,2);
  assert.equal((await api('/admin/after-sale/'+secondHistory[1].id+'/review',admin.token,'POST',{approve:true})).status,409);
  await ok('/admin/after-sale/'+secondHistory[0].id+'/review',admin.token,'POST',{approve:false,note:'隔离联调结束，验证发货解除'});await ok('/admin/order/ship/'+second.orderId,admin.token,'PUT',{trackingNo:'ISOLATED-TRACK'});
  assert.equal((await api('/fore/after-sale',buyer.token,'POST',{orderId:second.orderId,reason:'已发货不能申请'},{'Idempotency-Key':crypto.randomUUID()})).status,409);
  const unpaid=await purchase(1,false);assert.equal((await api('/fore/after-sale',buyer.token,'POST',{orderId:unpaid.orderId,reason:'未付款不能申请'},{'Idempotency-Key':crypto.randomUUID()})).status,409);await ok('/fore/order/cancel/'+unpaid.orderId,buyer.token,'PUT');
  checks.push('真实拒绝与重新申请保留历史，已拒绝申请不能再批准；无活跃售后后可发货，已发货和未付款申请被拒绝');
  const otherName='e2e_other_'+suffix;await ok('/fore/user/register',null,'POST',{username:otherName,password:'testOther123'});const other=await login(otherName,'testOther123',false);assert.equal((await api('/fore/after-sale/order/'+first.orderId,other.token)).status,404);assert.equal((await api('/fore/after-sale',other.token,'POST',{orderId:first.orderId,reason:'不能申请他人订单'},{'Idempotency-Key':crypto.randomUUID()})).status,404);assert.equal((await api('/fore/after-sale/order/'+first.orderId,admin.token)).status,403);assert.equal((await api('/admin/after-sale/list',buyer.token)).status,403);assert.equal((await api('/admin/after-sale/list',limited.token)).status,403);
  for(const width of [1280,1440,1920]){await rootPage.setViewportSize({width,height:650});assert.equal((await rootPage.locator('.admin-side').boundingBox()).y,0);assert.equal((await rootPage.locator('.admin-top').boundingBox()).y,0);assert(await drawer.getByRole('button',{name:'关闭',exact:true}).isVisible());assert(await rootPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))}
  checks.push('真实权限域互相隔离，三种桌面宽度及矮窗口导航固定、抽屉操作可见且无横向溢出');
  assert.deepEqual(errors,[]);await fs.writeFile(path.join(out,'真实浏览器联调.json'),JSON.stringify({checks,pageErrors:errors,events,response_faults:cutCount,backend_api_mocked:false,normal_refund_channel:'真实应用内的同步模拟通道'},null,2));console.log(JSON.stringify({groups:checks.length,pageErrors:errors.length,apiMocked:false}));
 }finally{await fs.writeFile(path.join(out,'请求核对.json'),JSON.stringify(events,null,2));for(const ctx of browser.contexts()){for(const p of ctx.pages()){await p.screenshot({path:path.join(out,'调试-'+browser.contexts().indexOf(ctx)+'-'+ctx.pages().indexOf(p)+'.png')}).catch(()=>{});await fs.writeFile(path.join(out,'输入核对.json'),JSON.stringify(await p.locator('.el-drawer input').evaluateAll(inputs=>inputs.map(i=>({type:i.type,value:i.type==='password'?'length:'+i.value.length:i.value,label:i.closest('.el-form-item')?.querySelector('label')?.textContent}))).catch(()=>[]),null,2))}}for(const token of tokens){try{await api('/common/auth/logout',token,'POST')}catch{}}await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e.stack);process.exit(1)});
