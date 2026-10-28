# Qivo 商品交易与运营平台

基于 Spring Boot 与 Vue 的单商城项目，包含消费者商城和管理员工作台。

## 业务范围

- 消费者：分类搜索、多规格商品、购物车、订单、收藏、收货地址、评价及本人账号设置。
- 管理员：商品与规格库存、订单发货、售后审核、统计、管理员账号和角色权限。
- 交易保障：库存条件扣减、下单幂等、库存流水、状态竞争处理、资源归属校验及授权版本审计。
- 付款及退款为模拟流程，售后仅支持已付款未发货订单整单退款，不产生真实资金交易。

## 工程与环境

`server/` 为单个 Maven 后端，`web/` 为 Vue 前端，`assets/` 保存商品初始化素材，`scripts/` 保存显式初始化工具。

需要 JDK 17、Maven、MySQL 8.0.16+、Redis 和符合 web/package.json 要求的 Node.js。后端采用 Spring Boot、MyBatis-Plus、Spring Security、JWT；前端采用 Vue 3、TypeScript、Element Plus、Pinia。

## 首次配置

创建私有 `server/src/main/resources/application-dev.yml`，按本机情况填写下列格式。真实配置、账号密码和密钥不提交 Git，也不打入 JAR。

```yaml
qivo:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: qivo
    username: 自行填写
    password: 自行填写
  redis:
    host: localhost
    port: 6379
    password: 自行填写
  flyway:
    enabled: true
  jwt:
    secret: 自行填写至少32字节的随机密钥
  upload:
    path: D:/qivo-upload
  swagger-ui:
    enabled: true
  api-docs:
    enabled: true
```

Redis无密码时填写空字符串。JWT密钥使用至少32字节随机值，上传目录使用本机绝对路径。

## 首次初始化

从根目录运行：

```powershell
.\scripts\初始化项目.ps1
```

脚本自动构建并检查目标库，复用开发配置执行迁移、生成权限、开发账号和商品封面。交互输入管理员和消费者初始密码，交易样例只询问一次，默认不添加。任何业务表已有数据时整次跳过，不执行迁移或重设密码。

账号为 admin、product_admin、buyer、buyer_other。两个管理员使用输入的管理员密码，两个消费者使用输入的消费者密码，均采用BCrypt存储，仅供本地开发。脚本会查找PATH、MAVEN_HOME/M2_HOME及运行中的IDEA内置Maven；也可用 -MavenExecutable 指定路径。

## 日常启动

已有数据无需初始化，直接从IDE启动 `com.qivo.QivoApplication`。前端在web目录运行：

```powershell
npm ci
npm run dev
```

默认后端8080、前端5173。Vite代理/api和/upload；商城入口/，后台入口/admin/login。消费者和管理员会话独立，新标签页独立登录。

## 构建与验证

```powershell
mvn -f server/pom.xml package
cd web
npm run test:unit
npm run build
```

默认后端测试使用隔离环境。真实MySQL专项需显式启用，并创建随机测试库，不应对已有业务库手动运行初始化。浏览器脚本使用API夹具，需安装Playwright和对应浏览器，或通过QIVO_PLAYWRIGHT_MODULE指定本机模块。部分截图和测量结果输出到本地docs目录，该目录不提交。

## 数据维护

保留V1～V9数据库迁移及后续新增版本，不修改已执行迁移。首次初始化失败可能保留已迁移的空表，修复原因后可重试；业务数据与新增图片有回滚保护。已有库升级前应备份并在副本验证。换电脑保留现有数据时，迁移数据库备份（含Flyway历史）及上传图片目录，不重新初始化。

[后端说明](server/README.md)　[前端说明](web/README.md)
