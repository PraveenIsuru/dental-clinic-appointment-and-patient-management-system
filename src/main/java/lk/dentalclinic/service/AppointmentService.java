package lk.dentalclinic.service;

import lk.dentalclinic.dao.AppointmentDao;
import lk.dentalclinic.dao.DentistDao;
import lk.dentalclinic.dao.DuplicateKeyException;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.dao.SettingsDao;
import lk.dentalclinic.dao.TreatmentDao;
import lk.dentalclinic.dao.jdbc.TransactionManager;
import lk.dentalclinic.event.AppointmentBookedEvent;
import lk.dentalclinic.event.EventBus;
import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.model.AppointmentStatus;
import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.validation.AppointmentValidator;
import lk.dentalclinic.validation.Rules;
import lk.dentalclinic.validation.ValidationResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Booking, searching and changing appointments — brief requirements 2 and 3.
 *
 * <p>FACADE: {@link #book} hides five DAOs, a settings load, a validator, a patient
 * creation, an appointment-number allocation and a transaction behind one call. The
 * handler above needs to know none of it.
 *
 * <p>Realises the Book Appointment sequence diagram, including its two-layer defence
 * against double booking: an optimistic availability check that produces a helpful
 * message, and the {@code uq_dentist_slot} violation that catches the race the check
 * cannot.
 */
public final class AppointmentService {

    private static final Logger LOG = Logger.getLogger(AppointmentService.class.getName());

    /** How many alternatives to offer when a slot is taken. */
    private static final int SUGGESTION_COUNT = 3;

    private final AppointmentDao appointmentDao;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final SettingsDao settingsDao;
    private final AppointmentAccessPolicy accessPolicy;
    private final TransactionManager transactions;
    private final EventBus eventBus;

    public AppointmentService(AppointmentDao appointmentDao, PatientDao patientDao,
                              DentistDao dentistDao, TreatmentDao treatmentDao,
                              SettingsDao settingsDao, AppointmentAccessPolicy accessPolicy,
                              TransactionManager transactions, EventBus eventBus) {
        this.appointmentDao = appointmentDao;
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.settingsDao = settingsDao;
        this.accessPolicy = accessPolicy;
        this.transactions = transactions;
        this.eventBus = eventBus;
    }

    public ClinicSettings settings() {
        return ClinicSettings.load(settingsDao);
    }

    // ------------------------------------------------------------------ booking

    /**
     * Registers an appointment — requirement 2.
     *
     * @throws ValidationException      a field rule failed; every failure is reported
     * @throws SlotUnavailableException the dentist is already booked at that time
     */
    public Appointment book(BookingRequest request, Session actor) {
        ClinicSettings settings = settings();
        Optional<Dentist> dentist = Optional.ofNullable(request.dentistId())
                .flatMap(dentistDao::findById);
        Optional<Treatment> treatment = Optional.ofNullable(request.treatmentId())
                .flatMap(treatmentDao::findById);

        ValidationResult validation =
                new AppointmentValidator(settings).validate(request, dentist, treatment);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        requireWithinBookingLimit(request, actor, settings);

        // Optimistic check: produces the friendly message with suggestions. It can be
        // overtaken by a concurrent booking, which the catch block below handles.
        requireSlotFree(request.dentistId(), request.appointmentDate(),
                request.appointmentTime(), settings, null);

        Appointment booked;
        try {
            booked = transactions.inTransactionAs(actor.getUserId(), () -> {
                int patientId = resolvePatientId(request);
                String appointmentNo =
                        appointmentDao.nextAppointmentNo(request.appointmentDate().getYear());

                Appointment appointment = Appointment.builder()
                        .appointmentNo(appointmentNo)
                        .patientId(patientId)
                        .dentistId(request.dentistId())
                        .treatmentId(request.treatmentId())
                        .appointmentDate(request.appointmentDate())
                        .appointmentTime(request.appointmentTime())
                        .status(AppointmentStatus.BOOKED)
                        .notes(blankToNull(request.notes()))
                        .build();

                int id = appointmentDao.insert(appointment, actor.getUserId());
                LOG.info(() -> "Booked " + appointmentNo + " by " + actor.getUsername());
                // Re-read so the event carries the patient, dentist and treatment a
                // notification needs, without the listener touching the database.
                return appointmentDao.findByNumberDetailed(appointmentNo)
                        .orElseGet(() -> appointment.toBuilder().appointmentId(id).build());
            });
        } catch (DuplicateKeyException e) {
            throw translateSlotClash(e, request.dentistId(), request.appointmentDate(),
                    request.appointmentTime(), settings);
        }

        // OBSERVER, published AFTER the commit. Realises the <<extend>> relationship
        // "Send Confirmation extends Book Appointment [booking succeeded]". Inside the
        // transaction a listener could act on a booking about to be rolled back.
        eventBus.publish(AppointmentBookedEvent.of(booked, actor.getUsername()));
        return booked;
    }

    /**
     * Refuses a patient who already holds the maximum number of upcoming appointments.
     *
     * <p>Without this one person can reserve a dentist's whole week from the self-service
     * portal and cancel the day before, and every slot they hold is a slot somebody who
     * needs it cannot book.
     *
     * <p><strong>Staff are exempt.</strong> A receptionist booking a course of six
     * treatments for one patient is doing their job, not hoarding slots. The rule is about
     * unsupervised self-service, so it applies to the role that has it.
     *
     * <p>Cancelled and past appointments do not count — {@code countUpcomingForPatient}
     * counts only BOOKED and CONFIRMED from today onwards, so cancelling frees the
     * allowance immediately.
     */
    private void requireWithinBookingLimit(BookingRequest request, Session actor,
                                           ClinicSettings settings) {
        if (actor.getRole() != lk.dentalclinic.model.RoleCode.PATIENT) {
            return;
        }
        Integer patientId = request.isForExistingPatient()
                ? request.patientId()
                : patientIdFor(actor).orElse(null);
        if (patientId == null) {
            return;
        }

        long upcoming = appointmentDao.countUpcomingForPatient(patientId);
        int limit = settings.maxUpcomingBookings();

        if (upcoming >= limit) {
            throw new BookingNotAllowedException(
                    "You already have " + limit + " upcoming appointments, which is the most "
                            + "we can hold for one patient. Cancel one, or telephone the clinic.");
        }
    }

    /** Creates the patient record when the booking carries typed details rather than an id. */
    private int resolvePatientId(BookingRequest request) {
        if (request.isForExistingPatient()) {
            return request.patientId();
        }
        Patient patient = Patient.builder()
                .patientNo(patientDao.nextPatientNo())
                .fullName(request.patientName().trim())
                .address(request.address().trim())
                .contactNumber(Rules.normalisePhone(request.contactNumber()))
                .email(blankToNull(request.email()))
                .build();
        return patientDao.create(patient);
    }

    // ------------------------------------------------------------------ lookup

    /**
     * Finds an appointment by number — requirement 3.
     *
     * <p>Case-insensitive, and scoped to what the session may see. A patient asking for
     * somebody else's number gets the same "not found" as for a number that does not
     * exist (A6).
     */
    public Appointment findByNumber(String appointmentNo, Session actor) {
        if (Rules.isBlank(appointmentNo)) {
            throw new AppointmentNotFoundException(String.valueOf(appointmentNo));
        }
        Appointment appointment = appointmentDao.findByNumberDetailed(appointmentNo.trim())
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));
        accessPolicy.requireView(appointment, actor);
        return appointment;
    }

    /** The list a session is entitled to see, for the appointments index page. */
    public List<Appointment> listFor(Session actor, LocalDate date) {
        return switch (actor.getRole()) {
            case ADMIN -> appointmentDao.findByDateDetailed(date);
            case DENTIST -> dentistDao.findByUserId(actor.getUserId())
                    .map(d -> appointmentDao.findByDentistAndDateDetailed(d.getId(), date))
                    .orElseGet(List::of);
            case PATIENT -> patientDao.findByUserId(actor.getUserId())
                    .map(p -> appointmentDao.findByPatientDetailed(p.getId()))
                    .orElseGet(List::of);
        };
    }

    /**
     * The patient record belonging to a signed-in patient, if there is one.
     *
     * <p>Used wherever a patient acts on their own behalf, so the id always comes from the
     * session and never from the request. A patient cannot book, or read, in another
     * patient's name by editing a field.
     */
    public Optional<Integer> patientIdFor(Session actor) {
        return patientDao.findByUserId(actor.getUserId()).map(Patient::getId);
    }

    public List<Appointment> upcomingFor(Session actor, int limit) {
        return switch (actor.getRole()) {
            case ADMIN -> appointmentDao.findUpcomingDetailed(limit);
            case DENTIST -> dentistDao.findByUserId(actor.getUserId())
                    .map(d -> appointmentDao.findByDentistAndDateDetailed(d.getId(), LocalDate.now()))
                    .orElseGet(List::of);
            case PATIENT -> patientDao.findByUserId(actor.getUserId())
                    .map(p -> appointmentDao.findUpcomingForPatientDetailed(p.getId(), limit))
                    .orElseGet(List::of);
        };
    }

    // ------------------------------------------------------------- availability

    /**
     * The times still free for a dentist on a date.
     *
     * <p>Every slot in the day, minus those already booked, minus those outside the
     * dentist's own session, minus any already in the past when the date is today.
     */
    public List<LocalTime> freeSlots(int dentistId, LocalDate date) {
        ClinicSettings settings = settings();
        Optional<Dentist> dentist = dentistDao.findById(dentistId);
        if (dentist.isEmpty() || date == null) {
            return List.of();
        }

        Set<LocalTime> taken = new HashSet<>(appointmentDao.bookedTimes(dentistId, date));
        boolean today = date.equals(LocalDate.now());
        LocalTime now = LocalTime.now();

        List<LocalTime> free = new ArrayList<>();
        for (LocalTime slot : settings.allSlots()) {
            if (taken.contains(slot)) {
                continue;
            }
            if (!dentist.get().isWithinSession(slot)) {
                continue;
            }
            if (today && slot.isBefore(now)) {
                continue;
            }
            free.add(slot);
        }
        return free;
    }

    /** The whole day for one dentist, free and busy, for the availability grid. */
    public List<SlotView> daySlots(int dentistId, LocalDate date) {
        ClinicSettings settings = settings();
        Optional<Dentist> dentist = dentistDao.findById(dentistId);
        if (dentist.isEmpty() || date == null) {
            return List.of();
        }

        List<Appointment> booked =
                appointmentDao.findByDentistAndDateDetailed(dentistId, date);
        boolean today = date.equals(LocalDate.now());
        LocalTime now = LocalTime.now();

        List<SlotView> slots = new ArrayList<>();
        for (LocalTime time : settings.allSlots()) {
            Optional<Appointment> occupant = booked.stream()
                    .filter(a -> a.getAppointmentTime().equals(time)
                            && a.getStatus() != AppointmentStatus.CANCELLED)
                    .findFirst();

            SlotState state;
            if (occupant.isPresent()) {
                state = SlotState.BOOKED;
            } else if (!dentist.get().isWithinSession(time)) {
                state = SlotState.OFF_DUTY;
            } else if (today && time.isBefore(now)) {
                state = SlotState.PAST;
            } else {
                state = SlotState.FREE;
            }
            slots.add(new SlotView(time, state, occupant.orElse(null)));
        }
        return slots;
    }

    /** One cell of the availability grid. */
    public record SlotView(LocalTime time, SlotState state, Appointment appointment) {

        public boolean isFree() {
            return state == SlotState.FREE;
        }
    }

    public enum SlotState { FREE, BOOKED, OFF_DUTY, PAST }

    // --------------------------------------------------------------- changes

    /** Moves an appointment to a new date and time, re-checking availability. */
    public Appointment reschedule(String appointmentNo, LocalDate date, LocalTime time,
                                  Session actor) {
        Appointment appointment = findByNumber(appointmentNo, actor);

        if (!accessPolicy.canReschedule(appointment, actor)) {
            throw new IllegalStateException(
                    "This appointment can no longer be changed. Please telephone the clinic.");
        }

        ClinicSettings settings = settings();
        ValidationResult validation = ValidationResult.empty();
        if (date == null || date.isBefore(LocalDate.now())) {
            validation.reject("appointmentDate", "Choose a date that has not passed.");
        }
        if (time == null || !settings.isWithinOpeningHours(time)) {
            validation.reject("appointmentTime", "The clinic is open from "
                    + settings.opensAt() + " to " + settings.closesAt() + ".");
        } else if (!settings.isOnSlotBoundary(time)) {
            validation.reject("appointmentTime",
                    "Appointments start every " + settings.slotMinutes() + " minutes.");
        }
        dentistDao.findById(appointment.getDentistId())
                .filter(d -> time != null && !d.isWithinSession(time))
                .ifPresent(d -> validation.reject("appointmentTime",
                        d.getFullName() + " works from " + d.getSessionStart()
                                + " to " + d.getSessionEnd() + "."));
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        // The appointment's own slot is not a clash with itself.
        requireSlotFree(appointment.getDentistId(), date, time, settings,
                appointment.getAppointmentId());

        try {
            transactions.inTransactionAs(actor.getUserId(), () ->
                    appointmentDao.updateSchedule(appointment.getAppointmentId(), date, time));
        } catch (DuplicateKeyException e) {
            throw translateSlotClash(e, appointment.getDentistId(), date, time, settings);
        }

        LOG.info(() -> "Rescheduled " + appointmentNo + " to " + date + " " + time
                + " by " + actor.getUsername());
        return appointment.rescheduledTo(date, time);
    }

    /**
     * Cancels an appointment.
     *
     * <p>No slot release is needed: {@code slot_active} is generated from the status, so
     * the row leaves {@code uq_dentist_slot} the moment it becomes CANCELLED and the time
     * is immediately bookable again. See {@code database/V4__cancelled_slots.sql}.
     */
    public void cancel(String appointmentNo, Session actor) {
        Appointment appointment = findByNumber(appointmentNo, actor);

        if (!accessPolicy.canReschedule(appointment, actor)) {
            throw new IllegalStateException(actor.getRole() == lk.dentalclinic.model.RoleCode.PATIENT
                    ? "Appointments can only be cancelled online more than 24 hours ahead. "
                      + "Please telephone the clinic."
                    : "This appointment can no longer be cancelled.");
        }
        if (appointment.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "This appointment is already " + appointment.getStatus() + ".");
        }

        transactions.inTransactionAs(actor.getUserId(), () ->
                appointmentDao.updateStatus(appointment.getAppointmentId(),
                        AppointmentStatus.CANCELLED));
        LOG.info(() -> "Cancelled " + appointmentNo + " by " + actor.getUsername());
    }

    public void confirm(String appointmentNo, Session actor) {
        changeStatus(appointmentNo, AppointmentStatus.CONFIRMED, actor);
    }

    /** Marks a treatment done, which is what makes the appointment billable in M4. */
    public void complete(String appointmentNo, Session actor) {
        Appointment appointment = findByNumber(appointmentNo, actor);
        if (!accessPolicy.canComplete(appointment, actor)) {
            throw new IllegalStateException("Only the treating dentist or an administrator "
                    + "can mark a treatment complete.");
        }
        applyStatus(appointment, AppointmentStatus.COMPLETED, actor);
    }

    private void changeStatus(String appointmentNo, AppointmentStatus next, Session actor) {
        Appointment appointment = findByNumber(appointmentNo, actor);
        if (actor.getRole() == lk.dentalclinic.model.RoleCode.PATIENT) {
            throw new IllegalStateException("Only clinic staff can change an appointment's status.");
        }
        applyStatus(appointment, next, actor);
    }

    private void applyStatus(Appointment appointment, AppointmentStatus next, Session actor) {
        if (!appointment.getStatus().canTransitionTo(next)) {
            throw new IllegalStateException("An appointment that is "
                    + appointment.getStatus() + " cannot become " + next + ".");
        }
        transactions.inTransactionAs(actor.getUserId(), () ->
                appointmentDao.updateStatus(appointment.getAppointmentId(), next));
        LOG.info(() -> "Appointment " + appointment.getAppointmentNo() + " -> " + next
                + " by " + actor.getUsername());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Refuses the booking if the slot is taken, offering alternatives.
     *
     * @param ignoreAppointmentId an appointment allowed to occupy the slot — itself,
     *                            when rescheduling to a time it already holds
     */
    private void requireSlotFree(int dentistId, LocalDate date, LocalTime time,
                                 ClinicSettings settings, Integer ignoreAppointmentId) {
        appointmentDao.findActiveAt(dentistId, date, time)
                .filter(existing -> ignoreAppointmentId == null
                        || existing.getAppointmentId() != ignoreAppointmentId)
                .ifPresent(existing -> {
                    throw new SlotUnavailableException(time, nearestFree(dentistId, date, time));
                });
    }

    /** Converts a unique-index violation into the same refusal the check would have given. */
    private SlotUnavailableException translateSlotClash(DuplicateKeyException e, int dentistId,
                                                        LocalDate date, LocalTime time,
                                                        ClinicSettings settings) {
        if (!e.isDentistSlotClash()) {
            throw e;   // some other uniqueness rule; not ours to reinterpret
        }
        LOG.warning(() -> "uq_dentist_slot caught a concurrent booking for dentist "
                + dentistId + " at " + date + " " + time);
        return new SlotUnavailableException(time, nearestFree(dentistId, date, time));
    }

    /** The free slots closest to the one the caller wanted. */
    private List<LocalTime> nearestFree(int dentistId, LocalDate date, LocalTime wanted) {
        return freeSlots(dentistId, date).stream()
                .sorted((a, b) -> Long.compare(
                        Math.abs(a.toSecondOfDay() - wanted.toSecondOfDay()),
                        Math.abs(b.toSecondOfDay() - wanted.toSecondOfDay())))
                .limit(SUGGESTION_COUNT)
                .sorted()
                .toList();
    }

    private static String blankToNull(String value) {
        return Rules.isBlank(value) ? null : value.trim();
    }
}
