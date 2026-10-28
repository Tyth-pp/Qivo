# Qivo 后端

Java 17、Spring Boot、MyBatis-Plus、MySQL、Redis、Spring Security与JWT。单个Maven工程，按controller、service、mapper、entity、dto、vo等职责划分包。

配置格式、首次初始化与日常启动见[根目录说明](../README.md)。私有application-dev.yml被忽略且不打入JAR，初始化命令只显式执行，不随应用启动。

```powershell
mvn package
```

默认测试使用隔离H2/Mock。显式首次初始化MySQL专项：

```powershell
mvn test '-Dtest=ProjectInitializationTests,ProjectInitializationMySqlIT' '-Dqivo.init.mysql=true'
```

该专项从本地开发配置读取连接，只写随机测试库并清理。其他真实MySQL专项同样需显式启用，运行前应检查测试适用条件。数据库版本由Flyway维护，不以源码存在推断某台机器已经完成迁移。
