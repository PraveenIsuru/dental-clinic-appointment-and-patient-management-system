package lk.dentalclinic.event;

import lk.dentalclinic.model.Appointment;

import java.time.Instant;

/**
 * Published after an appointment has been committed.
 *
 * <p>Realises the {@code <<extend>>} relationship from the use case diagram: <em>Send
 * Confirmation</em> extends <em>Book Appointment</em> {@code [booking succeeded]}. The
 * base use case is complete without it, which is exactly why it is an event and not a
 * step in the service method.
 *
 * <p>Carries the whole appointment rather than an id, so a listener needs no database
 * access to compose a message — and cannot see a later, changed version of the row.
 */
public record AppointmentBookedEvent(Appointment appointment,
                                     String bookedByUsername,
                                     Instant occurredAt) implements DomainEvent {

    public static AppointmentBookedEvent of(Appointment appointment, String bookedBy) {
        return new AppointmentBookedEvent(appointment, bookedBy, Instant.now());
    }

    @Override
    public String summary() {
        return "Appointment " + appointment.getAppointmentNo() + " booked by " + bookedByUsername;
    }
}
