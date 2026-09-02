package lk.dentalclinic.dao.jdbc;

import lk.dentalclinic.dao.HelpTopicDao;
import lk.dentalclinic.dao.RowMapper;
import lk.dentalclinic.model.HelpTopic;
import lk.dentalclinic.model.RoleCode;

import java.util.List;

public final class JdbcHelpTopicDao extends AbstractJdbcDao implements HelpTopicDao {

    private static final String SELECT = """
            SELECT topic_id, title, body, audience, display_order
            FROM help_topics
            """;

    private static final RowMapper<HelpTopic> MAPPER = rs -> new HelpTopic(
            rs.getInt("topic_id"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("audience"),
            rs.getInt("display_order"));

    public JdbcHelpTopicDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public List<HelpTopic> findAllOrdered() {
        return query(SELECT + " ORDER BY display_order, topic_id", MAPPER);
    }

    @Override
    public List<HelpTopic> findVisibleTo(RoleCode role) {
        // Filtered in SQL rather than in Java so the query returns only what the
        // viewer may see; the model's visibleTo() is the same rule, kept for the
        // unauthenticated help page where there is no role yet.
        String audiences = switch (role) {
            case ADMIN, DENTIST -> "('ALL', 'STAFF')";
            case PATIENT -> "('ALL', 'PATIENT')";
        };
        return query(SELECT + " WHERE audience IN " + audiences
                + " ORDER BY display_order, topic_id", MAPPER);
    }
}
