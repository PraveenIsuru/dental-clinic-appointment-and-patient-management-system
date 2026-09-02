package lk.dentalclinic.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OBSERVER — the in-process event bus.
 *
 * <p>Decouples "an appointment was booked" from everything that should follow it.
 * {@code AppointmentService} publishes the fact and does not know, or care, that a
 * notification listener exists. Adding an SMS reminder or an audit export later is a new
 * listener and no change to the service at all.
 *
 * <p><strong>Three decisions worth defending.</strong>
 *
 * <ol>
 *   <li><strong>Delivery is asynchronous, on a small background pool.</strong> The
 *       patient should not wait for a notification to be composed before their booking
 *       confirmation renders, and a slow listener must not hold an HTTP worker thread.</li>
 *   <li><strong>Publishers call this only after the transaction has committed.</strong>
 *       Publishing inside the transaction would let a listener observe — and act on — an
 *       appointment that the database is about to roll back. The rule lives at the call
 *       site because the bus has no way to know about a transaction it is not part of;
 *       this is the one place where a framework's {@code @TransactionalEventListener}
 *       would genuinely do better than hand-rolled code, and it is worth saying so.</li>
 *   <li><strong>A failing listener cannot break the publisher or the other listeners.</strong>
 *       Each delivery is wrapped; a thrown exception is logged and the next listener still
 *       runs. The business action has already committed, so there is nothing to undo.</li>
 * </ol>
 *
 * <p><em>The honest limitation.</em> Delivery is best-effort and in-memory: if the process
 * stops between commit and delivery, the event is simply lost. Nothing here is a durable
 * queue. That is acceptable because every listener is advisory — a lost confirmation
 * costs a telephone call, not a booking. It would not be acceptable for anything the
 * clinic's records depend on, and such a listener should not be added without replacing
 * this with a persistent outbox.
 */
public final class EventBus implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(EventBus.class.getName());
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    private final Map<Class<? extends DomainEvent>, List<EventListener<? extends DomainEvent>>>
            listeners = new ConcurrentHashMap<>();
    private final ExecutorService deliveries;

    public EventBus() {
        this(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "event-delivery");
            // Daemon: a pending notification must not keep the JVM alive at shutdown.
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** For tests, which pass a same-thread executor so assertions need no waiting. */
    public EventBus(ExecutorService deliveries) {
        this.deliveries = deliveries;
    }

    public <E extends DomainEvent> void subscribe(EventListener<E> listener) {
        listeners.computeIfAbsent(listener.eventType(), type -> new CopyOnWriteArrayList<>())
                .add(listener);
        LOG.fine(() -> "Subscribed " + listener.listenerName()
                + " to " + listener.eventType().getSimpleName());
    }

    /**
     * Publishes an event to every listener registered for its exact type.
     *
     * <p>Returns immediately; listeners run on the delivery pool. Call only after the
     * originating transaction has committed.
     */
    public void publish(DomainEvent event) {
        List<EventListener<? extends DomainEvent>> registered =
                listeners.getOrDefault(event.getClass(), List.of());

        if (registered.isEmpty()) {
            LOG.fine(() -> "No listeners for " + event.getClass().getSimpleName());
            return;
        }

        // Copied before handing to the pool: the list is concurrent, but a snapshot makes
        // the set of listeners for one event unambiguous.
        List<EventListener<? extends DomainEvent>> snapshot = new ArrayList<>(registered);
        try {
            deliveries.execute(() -> deliver(event, snapshot));
        } catch (RejectedExecutionException e) {
            // The pool is shutting down. Losing an advisory notification during shutdown
            // is preferable to failing a request that has already succeeded.
            LOG.log(Level.WARNING, "Event dropped during shutdown: " + event.summary(), e);
        }
    }

    /** Delivers on the calling thread. Used by tests that assert on the effect. */
    public void publishSync(DomainEvent event) {
        deliver(event, new ArrayList<>(listeners.getOrDefault(event.getClass(), List.of())));
    }

    @SuppressWarnings("unchecked")
    private void deliver(DomainEvent event, List<EventListener<? extends DomainEvent>> targets) {
        for (EventListener<? extends DomainEvent> listener : targets) {
            try {
                // Safe: subscribe() keys the map by the listener's own eventType().
                ((EventListener<DomainEvent>) listener).on(event);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Listener " + listener.listenerName()
                        + " failed on " + event.summary() + "; continuing with the rest", e);
            }
        }
    }

    public int listenerCount(Class<? extends DomainEvent> eventType) {
        return listeners.getOrDefault(eventType, List.of()).size();
    }

    @Override
    public void close() {
        deliveries.shutdown();
        try {
            if (!deliveries.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                deliveries.shutdownNow();
            }
        } catch (InterruptedException e) {
            deliveries.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
