package lk.dentalclinic.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    @DisplayName("falls back to built-in defaults when no file is present")
    void usesDefaultsWhenFileMissing() {
        AppConfig config = AppConfig.load(Path.of("does", "not", "exist.properties"));

        assertEquals(8080, config.serverPort());
        assertEquals(16, config.serverThreads());
        assertTrue(config.get("db.url").startsWith("jdbc:mysql://"));
    }

    @Test
    @DisplayName("values in the file override the defaults")
    void fileOverridesDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, """
                server.port=9191
                db.user=clinic_app
                """);

        AppConfig config = AppConfig.load(file);

        assertEquals(9191, config.serverPort());
        assertEquals("clinic_app", config.get("db.user"));
        // Untouched keys keep their default.
        assertEquals(16, config.serverThreads());
    }

    @Test
    @DisplayName("an unknown key fails loudly rather than returning null")
    void unknownKeyThrows() {
        AppConfig config = AppConfig.load(null);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> config.get("no.such.key"));
        assertTrue(thrown.getMessage().contains("no.such.key"));
    }

    @Test
    @DisplayName("a non-numeric port is reported with the offending value")
    void nonNumericIntThrows(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("application.properties");
        Files.writeString(file, "server.port=not-a-number\n");

        AppConfig config = AppConfig.load(file);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, config::serverPort);
        assertTrue(thrown.getMessage().contains("not-a-number"));
    }
}
