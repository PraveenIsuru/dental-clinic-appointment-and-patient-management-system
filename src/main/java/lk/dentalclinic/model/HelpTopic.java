package lk.dentalclinic.model;

/**
 * One entry in the help section - brief requirement 5, "step-by-step instructions
 * for new staff".
 *
 * <p>Held in the database rather than in a static page so an administrator can
 * revise the wording without a redeploy.
 *
 * @param audience {@code ALL}, {@code STAFF} or {@code PATIENT} - which roles see this topic
 */
public record HelpTopic(int topicId, String title, String body, String audience, int displayOrder) {

    /** Whether this topic should be shown to a viewer holding the given role. */
    public boolean visibleTo(RoleCode role) {
        return switch (audience == null ? "ALL" : audience.toUpperCase()) {
            case "ALL" -> true;
            case "STAFF" -> role == RoleCode.ADMIN || role == RoleCode.DENTIST;
            case "PATIENT" -> role == RoleCode.PATIENT;
            default -> false;
        };
    }

    /** The body split into paragraphs, so the view need not parse newlines itself. */
    public String[] paragraphs() {
        return body == null ? new String[0] : body.split("\\r?\\n");
    }
}
