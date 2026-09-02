package lk.icbt.dentalclinic.web;

import lk.icbt.dentalclinic.util.Html;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small template engine — the substitute for Thymeleaf.
 *
 * <p>Four constructs, and no more:
 *
 * <pre>
 *   {{name}}              escaped substitution  (the default, and the safe one)
 *   {{{fragment}}}        raw substitution, for HTML a handler has already built
 *   {{&gt;partial}}            include another template file
 *   {{#if flag}}...{{/if}}    include the block when the value is truthy
 *   {{#unless flag}}...{{/unless}}
 * </pre>
 *
 * <p><strong>What is missing, and why.</strong> There are no loops. A general
 * iteration construct needs nested scopes, a path expression syntax and its own
 * error reporting — several hundred lines that reimplement a solved problem badly.
 * Handlers instead build list markup in Java and pass it as one raw fragment, which
 * keeps the engine at a size that can be read in one sitting and understood
 * completely. The trade-off is that presentation logic for lists lives in Java rather
 * than in the template; that is a real cost and it is the right one at this size.
 *
 * <p>Templates are cached after first load. {@code reloadable} defeats the cache so a
 * template edit shows up without a restart during development.
 */
public final class TemplateEngine {

    private static final String ROOT = "/templates/";
    private static final int MAX_INCLUDE_DEPTH = 5;

    private static final Pattern RAW = Pattern.compile("\\{\\{\\{\\s*([\\w.]+)\\s*}}}");
    private static final Pattern ESCAPED = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");
    private static final Pattern INCLUDE = Pattern.compile("\\{\\{>\\s*([\\w./-]+)\\s*}}");
    private static final Pattern IF_BLOCK =
            Pattern.compile("\\{\\{#if\\s+([\\w.]+)\\s*}}(.*?)\\{\\{/if}}", Pattern.DOTALL);
    private static final Pattern UNLESS_BLOCK =
            Pattern.compile("\\{\\{#unless\\s+([\\w.]+)\\s*}}(.*?)\\{\\{/unless}}", Pattern.DOTALL);

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final boolean reloadable;

    public TemplateEngine() {
        this(false);
    }

    public TemplateEngine(boolean reloadable) {
        this.reloadable = reloadable;
    }

    /** Renders {@code /templates/<name>.html} against the model. */
    public String render(String name, Map<String, Object> model) {
        String template = load(name, 0);
        // Conditionals first: a block that is removed must not have its
        // substitutions evaluated, and an {{#if}} guarding a value is the usual reason
        // that value may be absent.
        String output = applyConditionals(template, model);
        output = substituteRaw(output, model);
        return substituteEscaped(output, model);
    }

    private String load(String name, int depth) {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new IllegalStateException(
                    "Template include depth exceeded at '" + name + "'; is there a cycle?");
        }
        String template = reloadable ? read(name) : cache.computeIfAbsent(name, this::read);
        return resolveIncludes(template, depth);
    }

    private String resolveIncludes(String template, int depth) {
        Matcher matcher = INCLUDE.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        matcher.reset();
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(load(matcher.group(1), depth + 1)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String read(String name) {
        String resource = ROOT + name + ".html";
        try (InputStream in = TemplateEngine.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("No such template: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read template " + resource, e);
        }
    }

    private static String applyConditionals(String template, Map<String, Object> model) {
        String output = replaceBlocks(template, IF_BLOCK, model, true);
        return replaceBlocks(output, UNLESS_BLOCK, model, false);
    }

    private static String replaceBlocks(String template, Pattern pattern,
                                        Map<String, Object> model, boolean keepWhenTruthy) {
        Matcher matcher = pattern.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            boolean truthy = isTruthy(model.get(matcher.group(1)));
            String body = (truthy == keepWhenTruthy) ? matcher.group(2) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(body));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Absent, null, false, an empty string and an empty collection are all falsy. */
    private static boolean isTruthy(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean b -> b;
            case CharSequence s -> !s.isEmpty();
            case java.util.Collection<?> c -> !c.isEmpty();
            case Number n -> n.doubleValue() != 0;
            default -> true;
        };
    }

    private static String substituteRaw(String template, Map<String, Object> model) {
        return substitute(template, RAW, model, false);
    }

    private static String substituteEscaped(String template, Map<String, Object> model) {
        return substitute(template, ESCAPED, model, true);
    }

    private static String substitute(String template, Pattern pattern,
                                     Map<String, Object> model, boolean escape) {
        Matcher matcher = pattern.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = model.get(matcher.group(1));
            // An absent key renders as empty rather than throwing: a half-rendered
            // page with a missing field is far more diagnosable than a 500.
            String text = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(escape ? Html.escape(text) : text));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
