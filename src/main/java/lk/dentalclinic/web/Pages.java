package lk.icbt.dentalclinic.web;

import lk.icbt.dentalclinic.util.Html;

/**
 * Self-contained error pages.
 *
 * <p>Deliberately built as string literals rather than through {@link TemplateEngine}:
 * these render on failure paths, including the path where a template could not be
 * loaded. A 500 page that itself throws while rendering leaves the user with a blank
 * response and nothing in the log to explain it.
 */
public final class Pages {

    private Pages() {
    }

    public static String forbidden(String homePath) {
        return page("403", "Not your area",
                "Your account does not have access to that page. If you think it should, "
                        + "ask an administrator.",
                homePath, "Back to your dashboard");
    }

    public static String notFound() {
        return page("404", "Nothing here",
                "That page does not exist. Check the address, or start again from the home page.",
                "/", "Back to the home page");
    }

    public static String csrfRejected(String homePath) {
        return page("403", "That form has expired",
                "For your security the form could not be submitted, usually because the page "
                        + "was left open too long. Open it again and re-enter your details.",
                homePath, "Back to your dashboard");
    }

    public static String serverError() {
        return page("500", "Something went wrong",
                "The problem has been logged. Please try again; if it keeps happening, "
                        + "tell an administrator what you were doing.",
                "/", "Back to the home page");
    }

    private static String page(String code, String heading, String message,
                               String linkHref, String linkText) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s &middot; Sunrise Dental Clinic</title>
                  <link rel="stylesheet" href="/css/app.css">
                </head>
                <body class="centred">
                  <main class="card">
                    <h1>%s</h1>
                    <h2>%s</h2>
                    <p>%s</p>
                    <p><a class="button" href="%s">%s</a></p>
                  </main>
                </body>
                </html>
                """.formatted(Html.escape(code), Html.escape(code), Html.escape(heading),
                Html.escape(message), Html.escape(linkHref), Html.escape(linkText));
    }
}
