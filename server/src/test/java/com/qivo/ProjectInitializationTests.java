package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.qivo.development.ProjectInitializationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectInitializationTests {
    @TempDir Path directory;

    @Test
    void loadsOnlyExplicitLocalConfigurationAndRedactsCredentials() throws Exception {
        Path config = directory.resolve("application-dev.yml");
        Files.writeString(config, """
                qivo:
                  datasource:
                    host: localhost
                    port: 3306
                    database: qivo_fresh
                    username: fixture_user
                    password: fixture-secret
                  upload:
                    path: ./target/init-images
                """);
        var settings = ProjectInitializationSettings.load(config);
        assertEquals("qivo_fresh", settings.database());
        assertEquals("fixture-secret", settings.password());
        assertFalse(settings.toString().contains("fixture-secret"));
        assertTrue(settings.upload().isAbsolute());
    }

    @Test
    void rejectsLegacySystemDatabaseAndUnsafeIdentifiers() {
        for (String database : new String[] {"mall", "qivo_dev", "mysql", "sys", "qivo;DROP", "a-b"}) {
            assertThrows(IllegalArgumentException.class, () -> new ProjectInitializationSettings(
                    "localhost", 3306, database, "fixture", "fixture", directory));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProjectInitializationSettings(
                "localhost/db?password=secret", 3306, "qivo", "fixture", "fixture", directory));
    }

    @Test
    void requiresARealAndCompleteConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> ProjectInitializationSettings.load(directory.resolve("missing.yml")));
    }
}
