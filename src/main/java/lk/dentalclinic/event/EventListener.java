package lk.dentalclinic.event;

/**
 * OBSERVER — something that reacts to a {@link DomainEvent}.
 *
 * <p>{@link #eventType()} rather than reflection on the generic parameter: type erasure
 * removes {@code E} at runtime, so the bus would have to inspect the class hierarchy to
 * recover it. An explicit method is three characters longer to write and impossible to
 * get subtly wrong.
 *
 * @param <E> the event this listener handles
 */
public interface EventListener<E extends DomainEvent> {

    Class<E> eventType();

    /**
     * Reacts to the event.
     *
     * <p>Must not throw for anything the publisher could have prevented: by the time this
     * runs the transaction has committed, so a failure here cannot undo the business
     * action. {@link EventBus} logs and continues rather than letting one listener stop
     * the others.
     */
    void on(E event);

    /** Name used in log lines. */
    default String listenerName() {
        return getClass().getSimpleName();
    }
}
