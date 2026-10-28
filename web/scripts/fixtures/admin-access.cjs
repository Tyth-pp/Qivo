// 仅供隔离浏览器测试；正式权限来自服务端 /admin/access。
const capabilities = Object.fromEntries(['dashboard','product:list','category:list','order:list','user:list','role:list','product:add','product:update','order:ship','order:price','user:disable','role:write','admin:list','admin:write','order:refund'].map(key => [key, true]));
module.exports = (adminId = 1) => ({ adminId, username: 'isolated-admin', capabilities });
