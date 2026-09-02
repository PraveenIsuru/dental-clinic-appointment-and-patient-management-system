package lk.dentalclinic.dao;

import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.model.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Appointments — the core of brief requirements 2 and 3.
 *
 * <p>Deferred from M2 deliberately so the method set would be driven by real callers
 * rather than guessed at. Every method below exists because a handler or a service
 * needed it, which is why there is no generic {@code findAll()}: a clinic's appointment
 * table grows without bound, and a screen that loads all of it works in testing and
 * fails in the third year of use.
 *
 * <p>Read methods come in two shapes. Those ending {@code Detailed} join the patient,
 * dentist and treatment rows because a view needs the names; the others fetch only the
 * appointment, because a service checking availability needs no names and joining four
 * tables for it would be waste.
 */
public interface AppointmentDao {

    // ------------------------------------------------------------------ reads

    /** Requirement 3: look up one appointment by its number, with everything a view needs. */
    Optional<Appointment> findByNumberDetailed(String appointmentNo);

    Optional<Appointment> findById(int appointmentId);

    /** A patient's own history, most recent first. */
    List<Appointment> findByPatientDetailed(int patientId);

    /** One dentist's day, in time order — the schedule view. */
    List<Appointment> findByDentistAndDateDetailed(int dentistId, LocalDate date);

    /** Everything booked on a date, for the administrator's day view. */
    List<Appointment> findByDateDetailed(LocalDate date);

    /** The next appointments from today onwards, capped. */
    List<Appointment> findUpcomingDetailed(int limit);

    List<Appointment> findUpcomingForPatientDetailed(int patientId, int limit);

    // ------------------------------------------------------- availability support

    /**
     * The times already taken for a dentist on a date. Cancelled appointments do not
     * count: their slots are free again, which {@code V4__cancelled_slots.sql} makes
     * true in the unique index as well.
     */
    List<LocalTime> bookedTimes(int dentistId, LocalDate date);

    /** The live appointment occupying this exact slot, if there is one. */
    Optional<Appointment> findActiveAt(int dentistId, LocalDate date, LocalTime time);

    // ------------------------------------------------------------------ writes

    /**
     * Allocates the next appointment number for a year via
     * {@code CALL sp_next_appointment_no(?, @no)}.
     *
     * <p>Must be called inside a transaction: the procedure takes a row lock on the
     * sequence table, and the lock is only held for the transaction's duration.
     */
    String nextAppointmentNo(int year);

    /** @return the generated appointment id */
    int insert(Appointment appointment, int createdByUserId);

    void updateSchedule(int appointmentId, LocalDate date, LocalTime time);

    /**
     * Moves an appointment to a new status.
     *
     * <p>Cancelling needs no separate slot release: {@code slot_active} is a generated
     * column that becomes NULL when the status is {@code CANCELLED}, so the row leaves
     * {@code uq_dentist_slot} of its own accord and the time is immediately bookable.
     */
    void updateStatus(int appointmentId, AppointmentStatus status);

    // ------------------------------------------------------------------ counts

    long countByStatusOn(LocalDate date, AppointmentStatus status);

    long countUpcomingForPatient(int patientId);

    long countForDentistOn(int dentistId, LocalDate date);
}
