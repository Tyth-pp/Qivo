package com.qivo.development;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** 只读取显式指定的本地开发配置，不启动应用，不读取命令行或系统环境中的凭据覆盖。 */
public record ProjectInitializationSettings(
        String host, int port, String database, String username, String password, Path upload) {
    private static final Set<String> BLOCKED = Set.of(
            "mall", "qivo_dev", "mysql", "information_schema", "performance_schema", "sys");

    public ProjectInitializationSettings {
        if (host == null || !host.matches("[a-zA-Z0-9._-]+") || port < 1 || port > 65535)
            throw new IllegalArgumentException("数据库主机或端口格式无效");
        if (database == null || !database.matches("[a-zA-Z0-9_]+")
                || BLOCKED.contains(database.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("目标库名无效，禁止初始化历史库或系统库");
        if (username == null || username.isBlank() || password == null || upload == null)
            throw new IllegalArgumentException("开发配置缺少数据库凭据或上传目录");
    }

    public static ProjectInitializationSettings load(Path file) throws Exception {
        if (!Files.isRegularFile(file))
            throw new IllegalArgumentException("请先填写 server/src/main/resources/application-dev.yml");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var sources = new YamlPropertySourceLoader().load("project-initialization", new FileSystemResource(file));
        // 与配置后面的文档覆盖前面的文档保持一致，不支持 profile 条件片段。
        for (var source : sources) {
            if (source.containsProperty("spring.config.activate.on-profile"))
                throw new IllegalArgumentException("初始化配置请使用单个 dev 配置，不使用条件片段");
            environment.getPropertySources().addFirst(source);
        }
        return new ProjectInitializationSettings(
                required(environment, "qivo.datasource.host"),
                Integer.parseInt(required(environment, "qivo.datasource.port")),
                required(environment, "qivo.datasource.database"),
                required(environment, "qivo.datasource.username"),
                environment.getRequiredProperty("qivo.datasource.password"),
                Path.of(required(environment, "qivo.upload.path")).toAbsolutePath().normalize());
    }

    private static String required(StandardEnvironment environment, String key) {
        String value = environment.getRequiredProperty(key);
        if (value.isBlank()) throw new IllegalArgumentException("开发配置未完整填写：" + key);
        return value;
    }

    public Connection connect(boolean selectDatabase) throws Exception {
        return DriverManager.getConnection(url(selectDatabase), username, password);
    }

    String url(boolean selectDatabase) {
        return "jdbc:mysql://" + host + ":" + port + "/" + (selectDatabase ? database : "")
                + "?characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=30000";
    }

    /** record 默认 toString 会包含密码，显式禁止输出。 */
    @Override
    public String toString() {
        return "ProjectInitializationSettings[database=" + database + ", credentials=REDACTED]";
    }
}
