package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.config.MyBatisConfig;
import com.qivo.entity.Product;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** 显式隔离基准：固定数据、同索引、成对测量实际 MyBatis 数据与计数查询。 */
@EnabledIfSystemProperty(named="qivo.query.benchmark",matches="true")
class ProductQueryBenchmarkIT {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private static final String BASELINE="benchmark.Baseline.selectPage";
    private static final String CANDIDATE="com.qivo.mapper.ProductMapper.selectPage";
    record Query(String name,int page,String keyword,Long category,Integer status,String sort) {}
    record Result(long total,List<Product> rows) {}

    @Test
    void compareQueryPlansAndMeasuredPaginationWithoutTouchingDailyTables() throws Exception {
        var settings=SkuMigrationIT.settings();
        assertEquals("qivo",settings.get("database"));
        assertEquals("qivo",System.getProperty("qivo.sku.source"));
        String base="jdbc:mysql://"+settings.get("host")+":"+settings.get("port")+"/";
        String options="?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED&rewriteBatchedStatements=true";
        String user=settings.get("username").toString(),password=settings.get("password").toString();
        String database="qivo_query_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var report=new LinkedHashMap<String,Object>();
        var measurements=new ArrayList<Map<String,Object>>();
        report.put("source","真实 MySQL + MyBatis 数据和分页计数；无 HTTP、Redis 或业务写入");
        report.put("database",database);report.put("samplesPerVariant",20);report.put("warmupsPerVariant",2);
        report.put("measurements",measurements);
        Path out=Path.of("../docs/商品查询性能专项");Files.createDirectories(out);
        try(var admin=DriverManager.getConnection(base+"qivo"+options,user,password)) {
            report.put("mysqlVersion",admin.getMetaData().getDatabaseProductVersion());
            try(var sql=admin.createStatement()) {sql.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");}
            try {
                Flyway.configure().dataSource(base+database+options,user,password).load().migrate();
                try(var ds=new HikariDataSource()) {
                    ds.setJdbcUrl(base+database+options);ds.setUsername(user);ds.setPassword(password);
                    ds.setMaximumPoolSize(8);ds.setMinimumIdle(0);
                    var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);
                    var resolver=new PathMatchingResourcePatternResolver();
                    var resources=new ArrayList<org.springframework.core.io.Resource>();
                    resources.addAll(Arrays.asList(resolver.getResources("classpath:mapper/*.xml")));
                    resources.addAll(Arrays.asList(resolver.getResources("classpath:benchmarks/product-page-baseline.xml")));
                    bean.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
                    bean.setPlugins(new MyBatisConfig().mybatisPlusInterceptor());
                    var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
                    configuration.setCacheEnabled(false);configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
                    bean.setConfiguration(configuration);var factory=Objects.requireNonNull(bean.getObject());
                    int previous=0;
                    for(int size:List.of(10000,50000,100000)) {
                        seed(ds,previous+1,size);previous=size;
                        try(var c=ds.getConnection();var sql=c.createStatement()) {sql.execute("ANALYZE TABLE product,product_sku,product_category");}
                        var queries=List.of(new Query("default",1,null,null,1,null),
                                new Query("category",1,null,5L,1,null),new Query("keyword",1,"匹配商品",null,1,null),
                                new Query("deep",Math.max(2,size/24),null,null,1,null),
                                new Query("sales",1,null,null,1,"sales"),new Query("price",1,null,null,1,"price_asc"),
                                new Query("admin-all",1,null,null,null,null),new Query("empty",1,"绝不匹配的关键词",null,1,null));
                        for(var query:queries) {
                            var before=select(factory,BASELINE,query);var after=select(factory,CANDIDATE,query);
                            assertEquals(json.writeValueAsString(before),json.writeValueAsString(after),query.name()+" 结果或总数不等价");
                            for(int i=0;i<2;i++){select(factory,BASELINE,query);select(factory,CANDIDATE,query);}
                            var oldTimes=new ArrayList<Double>();var newTimes=new ArrayList<Double>();
                            for(int i=0;i<20;i++) {
                                if(i%2==0){oldTimes.add(time(factory,BASELINE,query));newTimes.add(time(factory,CANDIDATE,query));}
                                else {newTimes.add(time(factory,CANDIDATE,query));oldTimes.add(time(factory,BASELINE,query));}
                            }
                            var row=new LinkedHashMap<String,Object>();row.put("products",size);row.put("scenario",query.name());
                            row.put("page",query.page());row.put("total",before.total());row.put("rows",before.rows().size());
                            row.put("equivalent",true);row.put("baseline",summary(oldTimes));row.put("candidate",summary(newTimes));measurements.add(row);
                            if(size==100000 && List.of("default","category","keyword","price").contains(query.name())) {
                                try(var c=ds.getConnection()) {
                                    for(var entry:Map.of("baseline",BASELINE,"candidate",CANDIDATE).entrySet()) {
                                        var params=params(query);var bound=factory.getConfiguration().getMappedStatement(entry.getValue()).getBoundSql(params);
                                        var plan=explain(c,bound,bound.getSql()+" LIMIT 0,12",params);
                                        String count=new PaginationInnerInterceptor(DbType.MYSQL).autoCountSql(new Page<>(1,12),bound.getSql());
                                        plan+="\n\nCOUNT:\n"+explain(c,bound,count,params);
                                        Files.writeString(out.resolve(query.name()+"-"+entry.getKey()+"-plan.txt"),plan);
                                    }
                                }
                            }
                        }
                        report.put("progress",size);
                        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("测量结果.json").toFile(),report);
                        System.out.println("已完成隔离商品查询规模："+size);
                    }
                    var concurrency=new ArrayList<Map<String,Object>>();
                    for(String statement:List.of(BASELINE,CANDIDATE)) {
                        var pool=Executors.newFixedThreadPool(4);var gate=new CountDownLatch(1);
                        var times=new ArrayList<Double>();long start=System.nanoTime();
                        try {
                            var futures=new ArrayList<Future<Double>>();
                            for(int i=0;i<24;i++)futures.add(pool.submit(()->{gate.await();return time(factory,statement,new Query("concurrent-default",1,null,null,1,null));}));
                            start=System.nanoTime();gate.countDown();for(var f:futures)times.add(f.get(60,TimeUnit.SECONDS));
                            var row=new LinkedHashMap<String,Object>();row.put("variant",statement.equals(BASELINE)?"baseline":"candidate");
                            row.put("concurrency",4);row.put("samples",24);row.put("latency",summary(times));
                            row.put("throughputPerSecond",24000000000.0/(System.nanoTime()-start));concurrency.add(row);
                        } finally {pool.shutdownNow();}
                    }
                    report.put("concurrentDefault",concurrency);
                    try(var c=ds.getConnection();var sql=c.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM product_sku")) {
                        rows.next();report.put("skus",rows.getLong(1));
                    }
                }
            } finally {
                assertTrue(database.matches("qivo_query_[a-f0-9]{12}"));
                try(var sql=admin.createStatement()){sql.execute("DROP DATABASE IF EXISTS "+database);}
                report.put("cleanupConfirmed",true);
                json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("测量结果.json").toFile(),report);
            }
        }
    }

    private Map<String,Object> params(Query q) {
        var values=new HashMap<String,Object>();values.put("page",new Page<Product>(q.page(),12));
        values.put("keyword",q.keyword());values.put("categoryId",q.category());values.put("status",q.status());values.put("sort",q.sort());return values;
    }
    private Result select(SqlSessionFactory factory,String statement,Query query) {
        var values=params(query);try(var session=factory.openSession()) {
            List<Product> rows=session.selectList(statement,values);
            return new Result(((Page<?>)values.get("page")).getTotal(),rows);
        }
    }
    private double time(SqlSessionFactory factory,String statement,Query query) {
        long start=System.nanoTime();select(factory,statement,query);return (System.nanoTime()-start)/1000000.0;
    }
    private Map<String,Object> summary(List<Double> times) {
        var sorted=times.stream().sorted().toList();return Map.of("meanMs",times.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                "p50Ms",sorted.get((sorted.size()-1)/2),"p95Ms",sorted.get((int)Math.ceil(sorted.size()*.95)-1),"samplesMs",times);
    }
    private String explain(Connection c,BoundSql bound,String sql,Map<String,Object> params) throws Exception {
        try(var stmt=c.prepareStatement("EXPLAIN ANALYZE "+sql)) {
            int i=1;for(var mapping:bound.getParameterMappings())stmt.setObject(i++,params.get(mapping.getProperty()));
            try(var rows=stmt.executeQuery()){var result=new StringBuilder();while(rows.next())result.append(rows.getString(1)).append('\n');return result.toString();}
        }
    }
    private void seed(HikariDataSource ds,int start,int end) throws Exception {
        try(var c=ds.getConnection()) {
            c.setAutoCommit(false);
            if(start==1)try(var categories=c.prepareStatement("INSERT INTO product_category(id,name,parent_id) VALUES(?,?,?)")) {
                for(int i=1;i<=20;i++){categories.setInt(1,i);categories.setString(2,"分类"+i);categories.setInt(3,i<=2?0:(i%2==0?1:2));categories.addBatch();}categories.executeBatch();
            }
            try(var products=c.prepareStatement("INSERT INTO product(id,name,description,category_id,price,status,sales,spec_mode,spec_definition,create_time) VALUES(?,?,?,?,?,?,?,?,?,?)");
                var skus=c.prepareStatement("INSERT INTO product_sku(product_id,sku_code,spec_key,spec_values,price,stock,status) VALUES(?,?,?,'[]',?,?,?)")) {
                for(int i=start;i<=end;i++) {
                    boolean single=i%3==0;
                    products.setInt(1,i);products.setString(2,(i%100==0?"匹配商品":"商品")+String.format("%08d",i));
                    products.setString(3,"固定规模测试商品简介");products.setInt(4,3+i%18);products.setInt(5,10+i%1000);
                    products.setInt(6,i%10==1?0:1);products.setInt(7,i%251);products.setString(8,single?"SINGLE":"MULTI");products.setString(9,"[]");
                    products.setTimestamp(10,Timestamp.valueOf(java.time.LocalDateTime.of(2026,1,1,0,0).plusMinutes(i%1000)));
                    products.addBatch();
                    for(int n=0;n<(single?1:4);n++) {
                        skus.setInt(1,i);skus.setString(2,"BENCH-"+i+"-"+n);skus.setString(3,single?"DEFAULT":"S"+n);
                        skus.setInt(4,10+i%1000+n*10);skus.setInt(5,i%20);skus.setString(6,i%17==0||n==3?"DISABLED":"ENABLED");skus.addBatch();
                    }
                    if(i%1000==0||i==end){products.executeBatch();skus.executeBatch();c.commit();}
                }
            }
        }
    }
}
