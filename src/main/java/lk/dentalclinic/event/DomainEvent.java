package lk.icbt.dentalclinic.event;

import java.time.Instant;

/**
 * Something that happened in the business tier and that other parts of the system may
 * want to react to.
 *
 * <p>A marker interface with one accessor, deliberately thin. An event is a statement of
 * fact in the past tense — it carries what happened, not what should be done about it.
 * That is what lets a listener be added or removed without the publisher changing.
 */
public interface DomainEvent {

    /** When the event occurred, not when it was delivered. */
    Instant occurredAt();

    /** A short description for the log. */
    String summary();
}
