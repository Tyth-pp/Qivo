package com.qivo;

import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 单工程内保留职责边界与接口协作，不依赖 Maven 模块拆分。 */
class ServiceStructureTests {
    private final Path application = Path.of("src/main/java/com/qivo");

    @Test
    void sharedAndModelPackagesDoNotImportBusinessInfrastructure() throws Exception {
        Pattern forbidden =
                Pattern.compile(
                        "(?:import\\s+|com\\.qivo\\.)(?:com\\.qivo\\.)?(mapper|service|controller|config|cache|persistence|properties|migration|handler)\\.");
        for (String folder :
                List.of(
                        "constant",
                        "enums",
                        "result",
                        "exception",
                        "annotation",
                        "utils",
                        "dto",
                        "entity",
                        "vo")) {
            try (var files = Files.walk(application.resolve(folder))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(forbidden.matcher(source).find(), () -> "反向依赖：" + file);
                    if (List.of("constant", "enums", "result", "exception", "annotation", "utils")
                            .contains(folder)) {
                        assertFalse(
                                source.contains("org.springframework."),
                                () -> "公共基础包不承载 Spring 组件：" + file);
                        assertFalse(
                                source.contains("com.qivo.entity.")
                                        || source.contains("com.qivo.dto.")
                                        || source.contains("com.qivo.vo."));
                    }
                }
            }
        }
    }

    @Test
    void controllersAndServicesCollaborateThroughInterfaces() throws Exception {
        for (String folder : List.of("controller", "service/impl")) {
            try (var files = Files.walk(application.resolve(folder))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    assertFalse(
                            Files.readString(file).contains("import com.qivo.service.impl."),
                            () -> "直接依赖实现类：" + file);
                }
            }
        }
        try (var files = Files.list(application.resolve("service/impl"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                assertTrue(name.endsWith("ServiceImpl"));
                Class<?> type = Class.forName("com.qivo.service.impl." + name);
                assertTrue(
                        Arrays.stream(type.getInterfaces())
                                .anyMatch(i -> i.getPackageName().equals("com.qivo.service")));
            }
        }
    }
}
