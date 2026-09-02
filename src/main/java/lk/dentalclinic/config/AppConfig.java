package lk.dentalclinic.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Immutable application configuration, resolved once at startup.
 *
 * <p>Resolution order, highest precedence first:
 * <ol>
 *   <li>environment variables ({@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD},
 *       {@code SERVER_PORT}) - this is what the deployment target sets in M6</li>
 *   <li>an external properties file, by default {@code config/application.properties}</li>
 *   <li>the built-in defaults below</li>
 * </ol>
 *
 * <p>Credentials are deliberately not hard-coded in source. The reference project
 * put them in {@code DBConnection.java}, which fails the brief's ETHICAL/DIGITAL
 * criterion on secure coding practices.
 */
public final class AppConfig {

    private static final String DEFAULT_FILE = "config/application.properties";

    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
    }

    /** Loads configuration from the default location, tolerating a missing file. */
    public static AppConfig load() {
        return load(Path.of(DEFAULT_FILE));
    }

    public static AppConfig load(Path file) {
        Properties p = new Properties();
        p.putAll(defaults());

        if (file != null && Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read configuration file " + file, e);
            }
        }

        overrideFromEnv(p, "DB_URL", "db.url");
        overrideFromEnv(p, "DB_USER", "db.user");
        overrideFromEnv(p, "DB_PASSWORD", "db.password");
        overrideFromEnv(p, "SERVER_PORT", "server.port");
        overrideFromEnv(p, "APP_ENVIRONMENT", "app.environment");
        overrideFromEnv(p, "COOKIE_SECURE", "cookie.secure");

        return new AppConfig(p);
    }

    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("server.port", "8080");
        p.setProperty("server.threads", "16");
        p.setProperty("db.url",
                "jdbc:mysql://localhost:3306/sunrise_clinic?useSSL=false&serverTimezone=Asia/Colombo");
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "");
        p.setProperty("db.pool.size", "10");
        p.setProperty("app.environment", "development");
        p.setProperty("cookie.secure", "false");
        return p;
    }

    private static void overrideFromEnv(Properties p, String envName, String key) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            p.setProperty(key, value);
        }
    }

    public String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("No configuration value for '" + key + "'");
        }
        return value;
    }

    public int getInt(String key) {
        String raw = get(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Configuration value '" + key + "' must be an integer, was: " + raw, e);
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key).trim());
    }

    public int serverPort() {
        return getInt("server.port");
    }

    public int serverThreads() {
        return getInt("server.threads");
    }

    /** In development, templates are re-read on every request so an edit shows up at once. */
    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(get("app.environment"));
    }

    /**
     * Whether to mark cookies {@code Secure}.
     *
     * <p>Off by default because the development server speaks plain HTTP and a
     * {@code Secure} cookie would simply never be sent, making sign-in appear broken.
     * The M6 deployment sets {@code COOKIE_SECURE=true} over TLS.
     */
    public boolean cookiesSecure() {
        return getBoolean("cookie.secure");
    }
}
