package lk.icbt.dentalclinic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A booked slot: one patient, one dentist, one treatment, at one date and time.
 *
 * <p>The identity that matters to the clinic is {@code (dentistId, date, time)} —
 * the tuple the unique index {@code uq_dentist_slot} protects. That constraint,
 * not this class, is what makes the no-double-booking rule true (A7).
 *
 * <p>References to the patient, dentist and treatment are held both as identifiers
 * and, when a view-facing query joined them, as fully loaded objects. A handler
 * rendering a list needs the names; a service checking availability needs only the
 * ids, and loading four extra rows for it would be waste.
 */
public final class Appointment {

    private final int appointmentId;
    private final String appointmentNo;
    private final int patientId;
    private final int dentistId;
    private final int treatmentId;
    private final LocalDate appointmentDate;
    private final LocalTime appointmentTime;
    private final AppointmentStatus status;
    private final String notes;
    private final LocalDateTime createdAt;

    private final Patient patient;
    private final Dentist dentist;
    private final Treatment treatment;

    private Appointment(Builder builder) {
        this.appointmentId = builder.appointmentId;
        this.appointmentNo = builder.appointmentNo;
        this.patientId = builder.patientId;
        this.dentistId = builder.dentistId;
        this.treatmentId = builder.treatmentId;
        this.appointmentDate = Objects.requireNonNull(builder.appointmentDate, "appointmentDate");
        this.appointmentTime = Objects.requireNonNull(builder.appointmentTime, "appointmentTime");
        this.status = builder.status == null ? AppointmentStatus.BOOKED : builder.status;
        this.notes = builder.notes;
        this.createdAt = builder.createdAt;
        this.patient = builder.patient;
        this.dentist = builder.dentist;
        this.treatment = builder.treatment;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public int getPatientId() {
        return patientId;
    }

    public int getDentistId() {
        return dentistId;
    }

    public int getTreatmentId() {
        return treatmentId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** Non-null only when loaded by a query that joined the patient row. */
    public Patient getPatient() {
        return patient;
    }

    public Dentist getDentist() {
        return dentist;
    }

    public Treatment getTreatment() {
        return treatment;
    }

    public LocalDateTime startsAt() {
        return LocalDateTime.of(appointmentDate, appointmentTime);
    }

    public boolean isPast() {
        return startsAt().isBefore(LocalDateTime.now());
    }

    public boolean isBillable() {
        return status.isBillable();
    }

    /** A patient may withdraw up to 24 hours beforehand; staff are not bound by that. */
    public boolean canBeCancelledBy(RoleCode role) {
        if (status.isTerminal()) {
            return false;
        }
        if (role == RoleCode.PATIENT) {
            return startsAt().isAfter(LocalDateTime.now().plusHours(24));
        }
        return true;
    }

    /** Returns a copy in the new state, or throws when the transition is not legal. */
    public Appointment withStatus(AppointmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Cannot move appointment " + appointmentNo + " from " + status + " to " + next);
        }
        return toBuilder().status(next).build();
    }

    public Appointment rescheduledTo(LocalDate date, LocalTime time) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot reschedule a " + status + " appointment (" + appointmentNo + ")");
        }
        return toBuilder().appointmentDate(date).appointmentTime(time).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .appointmentId(appointmentId)
                .appointmentNo(appointmentNo)
                .patientId(patientId)
                .dentistId(dentistId)
                .treatmentId(treatmentId)
                .appointmentDate(appointmentDate)
                .appointmentTime(appointmentTime)
                .status(status)
                .notes(notes)
                .createdAt(createdAt)
                .patient(patient)
                .dentist(dentist)
                .treatment(treatment);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int appointmentId;
        private String appointmentNo;
        private int patientId;
        private int dentistId;
        private int treatmentId;
        private LocalDate appointmentDate;
        private LocalTime appointmentTime;
        private AppointmentStatus status;
        private String notes;
        private LocalDateTime createdAt;
        private Patient patient;
        private Dentist dentist;
        private Treatment treatment;

        public Builder appointmentId(int appointmentId) {
            this.appointmentId = appointmentId;
            return this;
        }

        public Builder appointmentNo(String appointmentNo) {
            this.appointmentNo = appointmentNo;
            return this;
        }

        public Builder patientId(int patientId) {
            this.patientId = patientId;
            return this;
        }

        public Builder dentistId(int dentistId) {
            this.dentistId = dentistId;
            return this;
        }

        public Builder treatmentId(int treatmentId) {
            this.treatmentId = treatmentId;
            return this;
        }

        public Builder appointmentDate(LocalDate appointmentDate) {
            this.appointmentDate = appointmentDate;
            return this;
        }

        public Builder appointmentTime(LocalTime appointmentTime) {
            this.appointmentTime = appointmentTime;
            return this;
        }

        public Builder status(AppointmentStatus status) {
            this.status = status;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /** Also sets {@code patientId}, so the two can never disagree. */
        public Builder patient(Patient patient) {
            this.patient = patient;
            if (patient != null) {
                this.patientId = patient.getId();
            }
            return this;
        }

        public Builder dentist(Dentist dentist) {
            this.dentist = dentist;
            if (dentist != null) {
                this.dentistId = dentist.getId();
            }
            return this;
        }

        public Builder treatment(Treatment treatment) {
            this.treatment = treatment;
            if (treatment != null) {
                this.treatmentId = treatment.getTreatmentId();
            }
            return this;
        }

        public Appointment build() {
            return new Appointment(this);
        }
    }

    @Override
    public String toString() {
        return appointmentNo + " " + appointmentDate + " " + appointmentTime + " (" + status + ")";
    }
}
