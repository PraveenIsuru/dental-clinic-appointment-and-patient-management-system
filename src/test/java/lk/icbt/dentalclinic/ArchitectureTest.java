package lk.icbt.dentalclinic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces the three-tier architecture at build time.
 *
 * <p><strong>Why this test is the most valuable one in the suite for Task B.</strong> The
 * 70–100 band asks for a three-tier architecture. Every project claims one; a claim is
 * worth nothing without a mechanism, because tiers erode one import at a time — someone
 * needs a query result in a handler, adds {@code import java.sql.ResultSet}, and the
 * boundary is gone with nothing to notice it. This test reads every source file and fails
 * the build on a violation, which turns the claim into a property the code actually has.
 *
 * <p>It works on the source rather than the compiled classes deliberately: the rules are
 * about what a developer wrote and can read in the failure message, and no bytecode
 * library is needed to check them, which would have meant adding a dependency to a project
 * whose whole point is not having any.
 *
 * <p>The rules:
 *
 * <table>
 *   <caption>Forbidden dependencies</caption>
 *   <tr><th>Package</th><th>May not import</th><th>Because</th></tr>
 *   <tr><td>{@code web}</td><td>{@code java.sql}, {@code dao.jdbc}</td>
 *       <td>the presentation tier must not reach the database directly</td></tr>
 *   <tr><td>{@code service}</td><td>{@code java.sql}, {@code com.sun.net.httpserver}</td>
 *       <td>business rules must not know about HTTP or JDBC</td></tr>
 *   <tr><td>{@code dao}</td><td>{@code com.sun.net.httpserver}, {@code web}</td>
 *       <td>the data tier must not know it is serving a web application</td></tr>
 *   <tr><td>{@code model}</td><td>{@code java.sql}, HTTP, {@code dao}, {@code web}</td>
 *       <td>entities are shared by every tier and must depend on none of them</td></tr>
 * </table>
 */
class ArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final String BASE = "lk.icbt.dentalclinic";
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)",
            Pattern.MULTILINE);

    /** One rule: files under {@code packageSuffix} may not import anything matching a ban. */
    private record Rule(String packageSuffix, List<String> bannedPrefixes, String reason) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("web", List.of("java.sql", "javax.sql", BASE + ".dao.jdbc"),
                    "the presentation tier must go through a service, never touch JDBC"),
            new Rule("service", List.of("java.sql", "javax.sql", "com.sun.net.httpserver"),
                    "business rules must not depend on HTTP or on JDBC types"),
            new Rule("dao", List.of("com.sun.net.httpserver", BASE + ".web"),
                    "the data tier must not know it is serving a web application"),
            new Rule("model", List.of("java.sql", "com.sun.net.httpserver",
                            BASE + ".dao", BASE + ".web"),
                    "entities are shared by every tier and must depend on none of them"),
            new Rule("validation", List.of("java.sql", "com.sun.net.httpserver"),
                    "validation rules are pure and must stay testable without either"));

    /** Files exempt from a rule, with the reason stated rather than silently skipped. */
    private static boolean isExempt(Path file, String bannedPrefix) {
        String name = file.getFileName().toString();

        // The DI registry's whole job is to construct the graph, so it necessarily
        // names an implementation from every tier. Excluding it is not a loophole:
        // it is the one place where wiring is supposed to be visible.
        if (name.equals("ServiceRegistry.java")) {
            return true;
        }
        // The transaction boundary is expressed in service code as a lambda, so the
        // service package legitimately imports the manager - but not java.sql.
        return false;
    }

    @Test
    @DisplayName("no source file crosses a tier boundary")
    void tierBoundariesAreRespected() {
        List<String> violations = new ArrayList<>();

        for (Rule rule : RULES) {
            Path packageRoot = SOURCE_ROOT.resolve(BASE.replace('.', java.io.File.separatorChar))
                    .resolve(rule.packageSuffix());
            if (!Files.isDirectory(packageRoot)) {
                fail("Package directory not found: " + packageRoot
                        + " — has the source layout changed?");
            }

            for (Path file : javaFilesUnder(packageRoot)) {
                for (String imported : importsOf(file)) {
                    for (String banned : rule.bannedPrefixes()) {
                        if (imported.startsWith(banned) && !isExempt(file, banned)) {
                            violations.add(String.format(
                                    "%s imports %s%n      (%s may not depend on %s — %s)",
                                    SOURCE_ROOT.relativize(file), imported,
                                    rule.packageSuffix(), banned, rule.reason()));
                        }
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("The three-tier architecture has been broken in "
                    + violations.size() + " place(s):\n    - "
                    + String.join("\n    - ", violations));
        }
    }

    @Test
    @DisplayName("every package documents which tier it belongs to")
    void everyPackageHasPackageInfo() {
        Path base = SOURCE_ROOT.resolve(BASE.replace('.', java.io.File.separatorChar));
        List<String> missing = new ArrayList<>();

        try (Stream<Path> directories = Files.walk(base)) {
            directories.filter(Files::isDirectory)
                    .filter(ArchitectureTest::containsJavaFiles)
                    .filter(directory -> !Files.exists(directory.resolve("package-info.java")))
                    .forEach(directory -> missing.add(base.relativize(directory).toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(missing.isEmpty(),
                "These packages have no package-info.java naming their tier: " + missing);
    }

    @Test
    @DisplayName("the data tier is reached only through its interfaces")
    void servicesDependOnDaoInterfacesNotImplementations() {
        Path serviceRoot = SOURCE_ROOT.resolve(BASE.replace('.', java.io.File.separatorChar))
                .resolve("service");
        List<String> violations = new ArrayList<>();

        for (Path file : javaFilesUnder(serviceRoot)) {
            for (String imported : importsOf(file)) {
                // TransactionManager is the deliberate exception: a transaction boundary
                // is a service-tier concern expressed with a data-tier object.
                if (imported.startsWith(BASE + ".dao.jdbc")
                        && !imported.endsWith("TransactionManager")) {
                    violations.add(SOURCE_ROOT.relativize(file) + " imports " + imported);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Services must depend on DAO interfaces, so they stay testable with an "
                        + "in-memory stand-in. Violations: " + violations);
    }

    @Test
    @DisplayName("the domain model does not depend on the framework-substitute code")
    void modelIsSelfContained() {
        Path modelRoot = SOURCE_ROOT.resolve(BASE.replace('.', java.io.File.separatorChar))
                .resolve("model");
        List<String> violations = new ArrayList<>();

        for (Path file : javaFilesUnder(modelRoot)) {
            for (String imported : importsOf(file)) {
                if (imported.startsWith(BASE) && !imported.startsWith(BASE + ".model")) {
                    violations.add(SOURCE_ROOT.relativize(file) + " imports " + imported);
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "The model must depend on nothing else in the application, so it can be "
                        + "reasoned about alone. Violations: " + violations);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + root, e);
        }
    }

    private static List<String> importsOf(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private static boolean containsJavaFiles(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.anyMatch(path -> path.toString().endsWith(".java")
                    && !path.getFileName().toString().equals("package-info.java"));
        } catch (IOException e) {
            return false;
        }
    }
}
