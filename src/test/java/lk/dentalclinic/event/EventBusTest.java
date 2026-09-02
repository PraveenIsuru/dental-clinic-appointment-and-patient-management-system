package lk.icbt.dentalclinic.event;

import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.AppointmentStatus;
import lk.icbt.dentalclinic.model.Dentist;
import lk.icbt.dentalclinic.model.Patient;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    private static Appointment appointment() {
        return Appointment.builder()
                .appointmentId(1)
                .appointmentNo("APT-2026-0001")
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(9, 0))
                .status(AppointmentStatus.BOOKED)
                .patient(Patient.builder()
                        .id(1).patientNo("PAT-000001").fullName("Kasun Fernando")
                        .address("14/3 Temple Road").contactNumber("0771234567").build())
                .dentist(Dentist.builder()
                        .id(1).fullName("Dr. Nimal Perera").specialization("General Dentistry")
                        .sessionStart(LocalTime.of(8, 0)).sessionEnd(LocalTime.of(16, 0))
                        .active(true).build())
                .treatment(new Treatment(2, "CLEAN", "Scaling and Polishing",
                        TreatmentFamily.CLEANING, null, new BigDecimal("5000.00"), 30, true))
                .build();
    }

    /** A listener that records what it saw, on the calling thread. */
    private static final class RecordingListener
            implements EventListener<AppointmentBookedEvent> {

        final List<String> seen = new ArrayList<>();

        @Override
        public Class<AppointmentBookedEvent> eventType() {
            return AppointmentBookedEvent.class;
        }

        @Override
        public void on(AppointmentBookedEvent event) {
            seen.add(event.appointment().getAppointmentNo());
        }
    }

    @Test
    @DisplayName("a subscribed listener receives the event")
    void deliversToSubscriber() {
        EventBus bus = new EventBus(java.util.concurrent.Executors.newSingleThreadExecutor());
        RecordingListener listener = new RecordingListener();
        bus.subscribe(listener);

        bus.publishSync(AppointmentBookedEvent.of(appointment(), "admin"));

        assertEquals(List.of("APT-2026-0001"), listener.seen);
        bus.close();
    }

    @Test
    @DisplayName("an event with no listeners is simply ignored")
    void noListenersIsHarmless() {
        try (EventBus bus = new EventBus(
                java.util.concurrent.Executors.newSingleThreadExecutor())) {
            bus.publishSync(AppointmentBookedEvent.of(appointment(), "admin"));
            assertEquals(0, bus.listenerCount(AppointmentBookedEvent.class));
        }
    }

    @Test
    @DisplayName("listeners only receive the event type they registered for")
    void deliversByType() {
        try (EventBus bus = new EventBus(
                java.util.concurrent.Executors.newSingleThreadExecutor())) {
            RecordingListener bookings = new RecordingListener();
            AtomicInteger billsSeen = new AtomicInteger();

            bus.subscribe(bookings);
            bus.subscribe(new EventListener<BillIssuedEvent>() {
                @Override
                public Class<BillIssuedEvent> eventType() {
                    return BillIssuedEvent.class;
                }

                @Override
                public void on(BillIssuedEvent event) {
                    billsSeen.incrementAndGet();
                }
            });

            bus.publishSync(AppointmentBookedEvent.of(appointment(), "admin"));

            assertEquals(1, bookings.seen.size());
            assertEquals(0, billsSeen.get(), "a bill listener must not see a booking event");
        }
    }

    @Test
    @DisplayName("one failing listener does not stop the others")
    void failureIsIsolated() {
        try (EventBus bus = new EventBus(
                java.util.concurrent.Executors.newSingleThreadExecutor())) {
            RecordingListener healthy = new RecordingListener();

            bus.subscribe(new EventListener<AppointmentBookedEvent>() {
                @Override
                public Class<AppointmentBookedEvent> eventType() {
                    return AppointmentBookedEvent.class;
                }

                @Override
                public void on(AppointmentBookedEvent event) {
                    throw new IllegalStateException("the SMS gateway is down");
                }
            });
            bus.subscribe(healthy);

            // Must not propagate: the booking has already committed, so there is nothing
            // to undo and no caller who could act on the failure.
            bus.publishSync(AppointmentBookedEvent.of(appointment(), "admin"));

            assertEquals(1, healthy.seen.size(),
                    "the second listener must still run after the first threw");
        }
    }

    @Test
    @DisplayName("asynchronous delivery reaches the listener without blocking the publisher")
    void deliversAsynchronously() throws InterruptedException {
        EventBus bus = new EventBus();
        CountDownLatch delivered = new CountDownLatch(1);

        bus.subscribe(new EventListener<AppointmentBookedEvent>() {
            @Override
            public Class<AppointmentBookedEvent> eventType() {
                return AppointmentBookedEvent.class;
            }

            @Override
            public void on(AppointmentBookedEvent event) {
                delivered.countDown();
            }
        });

        bus.publish(AppointmentBookedEvent.of(appointment(), "admin"));

        assertTrue(delivered.await(5, TimeUnit.SECONDS),
                "the listener should run on the delivery pool");
        bus.close();
    }

    @Test
    @DisplayName("publishing after shutdown is dropped, not thrown")
    void publishAfterShutdownIsDropped() {
        EventBus bus = new EventBus();
        bus.subscribe(new RecordingListener());
        bus.close();

        // Losing an advisory notification during shutdown must not fail a request that
        // has already succeeded.
        bus.publish(AppointmentBookedEvent.of(appointment(), "admin"));
    }

    @Test
    @DisplayName("the notification listener composes a message naming the appointment")
    void notificationListenerComposesMessage() {
        AppointmentNotificationListener listener = new AppointmentNotificationListener();

        listener.on(AppointmentBookedEvent.of(appointment(), "admin"));

        assertEquals(1, listener.sentCount());
        var notification = listener.recent().get(0);
        assertEquals("0771234567", notification.recipient());
        assertTrue(notification.body().contains("APT-2026-0001"));
        assertTrue(notification.body().contains("Kasun Fernando"));
        assertTrue(notification.body().contains("Dr. Nimal Perera"));
        assertTrue(notification.body().contains("Scaling and Polishing"));
    }

    @Test
    @DisplayName("a patient with no contact number produces no notification and no error")
    void noContactNumberIsHandled() {
        AppointmentNotificationListener listener = new AppointmentNotificationListener();
        Appointment noNumber = appointment().toBuilder()
                .patient(Patient.builder().id(1).patientNo("PAT-000001")
                        .fullName("No Number").address("Somewhere").build())
                .build();

        listener.on(AppointmentBookedEvent.of(noNumber, "admin"));

        assertEquals(0, listener.sentCount());
    }

    @Test
    @DisplayName("the kept-message list is bounded so a long run cannot grow without limit")
    void recentListIsBounded() {
        AppointmentNotificationListener listener = new AppointmentNotificationListener();

        for (int i = 0; i < 50; i++) {
            listener.on(AppointmentBookedEvent.of(appointment(), "admin"));
        }

        assertEquals(20, listener.sentCount());
        assertFalse(listener.recent().size() > 20);
    }
}
