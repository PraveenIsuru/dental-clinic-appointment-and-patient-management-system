package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.dao.HelpTopicDao;
import lk.dentalclinic.model.HelpTopic;
import lk.dentalclinic.util.Html;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The help section — brief requirement 5, "step-by-step instructions for new staff".
 *
 * <p>Public, but role-aware: a signed-in user sees the topics for their role, and a
 * visitor who is not signed in sees only the general ones. That is why
 * {@link lk.dentalclinic.web.filter.SessionFilter} identifies without
 * authorising — this page needs to know who is reading it without requiring anyone
 * to sign in.
 *
 * <p>The topic list is built here as an HTML fragment rather than looped over in the
 * template, which is the trade the template engine makes explicit: no loop construct,
 * so list markup lives in Java.
 */
public final class HelpHandler implements Handler {

    private final HelpTopicDao helpTopicDao;
    private final View view;

    public HelpHandler(HelpTopicDao helpTopicDao, View view) {
        this.helpTopicDao = helpTopicDao;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<HelpTopic> topics = WebContext.session()
                .map(session -> helpTopicDao.findVisibleTo(session.getRole()))
                .orElseGet(() -> helpTopicDao.findAllOrdered().stream()
                        .filter(topic -> "ALL".equalsIgnoreCase(topic.audience()))
                        .toList());

        Map<String, Object> model = view.model(exchange);
        model.put("topicCount", topics.size());
        model.put("topics", renderTopics(topics));
        view.render(exchange, "help", model);
    }

    private static String renderTopics(List<HelpTopic> topics) {
        if (topics.isEmpty()) {
            return "<p class=\"muted\">No help topics have been published yet.</p>";
        }
        StringBuilder html = new StringBuilder();
        for (HelpTopic topic : topics) {
            html.append("<article class=\"card help-topic\">\n")
                    .append("  <h2>").append(Html.escape(topic.title())).append("</h2>\n");
            for (String paragraph : topic.paragraphs()) {
                if (!paragraph.isBlank()) {
                    // Escaped, then wrapped: the body is administrator-editable content
                    // and must never be able to inject markup into this page.
                    html.append("  <p>").append(Html.escape(paragraph)).append("</p>\n");
                }
            }
            html.append("</article>\n");
        }
        return html.toString();
    }
}
