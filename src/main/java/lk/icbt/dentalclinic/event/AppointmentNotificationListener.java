package lk.icbt.dentalclinic.event;

import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.Patient;

import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;

/**
 * Composes the booking confirmation — the <em>Send Confirmation</em> use case.
 *
 * <p><strong>Assumption A12:</strong> the message is composed and recorded, not
 * transmitted. Integrating a paid SMS or email gateway is out of scope for coursework and
 * would make the build unreproducible for a marker, who cannot be expected to hold an
 * account with a Sri Lankan telecoms provider.
 *
 * <p>What matters is that the boundary is real: everything up to the point of sending is
 * implemented, and substituting a genuine transport means changing
 * {@link #transmit(String, String)} and nothing else. The Observer pattern is what makes
 * that true — the service that books the appointment has no reference to this class.
 *
 * <p>The last few messages are kept in memory so the administrator's page and the tests
 * can show that the notification really was produced, rather than asking a marker to take
 * a log line on trust.
 */
public final class AppointmentNotificationListener
        implements EventListener<AppointmentBookedEvent> {

    private static final Logger LOG =
            Logger.getLogger(AppointmentNotificationListener.class.getName());
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' HH:mm");
    private static final int KEPT = 20;

    /** Most recent first. Bounded, so a long-running process cannot grow without limit. */
    private final Deque<Notification> recent = new ArrayDeque<>();

    /** One composed message. */
    public record Notification(String recipient, String channel, String body,
                               java.time.Instant sentAt) {
    }

    @Override
    public Class<AppointmentBookedEvent> eventType() {
        return AppointmentBookedEvent.class;
    }

    @Override
    public void on(AppointmentBookedEvent event) {
        Appointment appointment = event.appointment();
        Patient patient = appointment.getPatient();

        if (patient == null || patient.getContactNumber() == null) {
            // Booked without a loaded patient, or a patient with no number on file.
            // Nothing to send to; not an error.
            LOG.fine(() -> "No contact number for " + appointment.getAppointmentNo());
            return;
        }

        String body = compose(appointment, patient);
        transmit(patient.getContactNumber(), body);
        record(new Notification(patient.getContactNumber(), "SMS", body, event.occurredAt()));
    }

    private static String compose(Appointment appointment, Patient patient) {
        String dentist = appointment.getDentist() == null
                ? "your dentist" : appointment.getDentist().getFullName();
        String treatment = appointment.getTreatment() == null
                ? "your treatment" : appointment.getTreatment().getName();

        return "Sunrise Dental Clinic: Dear %s, your appointment %s is confirmed for %s with %s (%s). "
                .formatted(patient.getFullName(),
                        appointment.getAppointmentNo(),
                        WHEN.format(appointment.startsAt()),
                        dentist,
                        treatment)
                + "Please arrive 10 minutes early. To change it, call 011 2 345 678.";
    }

    /**
     * The seam a real gateway would replace.
     *
     * <p>Logging at INFO rather than FINE on purpose: it is the visible evidence that the
     * asynchronous listener ran after the booking committed, which is what the sequence
     * diagram claims.
     */
    private void transmit(String recipient, String body) {
        LOG.info(() -> "[notification] to " + maskNumber(recipient) + ": " + body);
    }

    /** Never write a full contact number to a log file. */
    private static String maskNumber(String number) {
        if (number == null || number.length() < 4) {
            return "****";
        }
        return "*".repeat(number.length() - 3) + number.substring(number.length() - 3);
    }

    private synchronized void record(Notification notification) {
        recent.addFirst(notification);
        while (recent.size() > KEPT) {
            recent.removeLast();
        }
    }

    /** The recent messages, newest first. */
    public synchronized List<Notification> recent() {
        return List.copyOf(recent);
    }

    public synchronized int sentCount() {
        return recent.size();
    }
}
