package lk.dentalclinic.util;

/** HTML escaping. */
public final class Html {

    private Html() {
    }

    /**
     * Escapes text for insertion into HTML.
     *
     * <p>Applied by default to every value the template engine substitutes, so
     * output is safe unless a developer deliberately opts out with the raw
     * {@code {{{...}}} } syntax. Escaping by default and opting out is the right way
     * round: forgetting to escape is silent and exploitable, whereas forgetting to
     * opt out is visible immediately as literal markup on the page.
     *
     * <p>Both quote characters are escaped, not only {@code <} and {@code >}, because
     * a value may be substituted inside an attribute where an unescaped quote would
     * end the attribute and begin a new one.
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Escapes text and turns newlines into {@code <br>}, for multi-line help bodies. */
    public static String escapeWithBreaks(String raw) {
        return escape(raw).replace("\n", "<br>\n");
    }
}
