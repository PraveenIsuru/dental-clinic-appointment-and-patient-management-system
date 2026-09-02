package lk.dentalclinic.model;

import java.time.LocalTime;

/**
 * A dentist, with the session hours the booking validator checks against.
 *
 * <p>Clinic hours (08:00-20:00) and a dentist's own session are separate rules:
 * an appointment must satisfy both. The seeded dentists deliberately have
 * different sessions so the rule is testable (assumption A8).
 */
public final class Dentist extends Person {

    private final String specialization;
    private final LocalTime sessionStart;
    private final LocalTime sessionEnd;
    private final boolean active;

    private Dentist(Builder builder) {
        super(builder.id, builder.fullName, builder.phone, builder.email, builder.userId);
        this.specialization = builder.specialization;
        this.sessionStart = builder.sessionStart;
        this.sessionEnd = builder.sessionEnd;
        this.active = builder.active;
    }

    public String getSpecialization() {
        return specialization;
    }

    public LocalTime getSessionStart() {
        return sessionStart;
    }

    public LocalTime getSessionEnd() {
        return sessionEnd;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Whether a time falls inside this dentist's working session.
     *
     * <p>The start is inclusive and the end exclusive: a session ending at 16:00
     * means the last bookable slot starts before 16:00, because an appointment
     * beginning exactly at the end of a session would run past it.
     */
    public boolean isWithinSession(LocalTime time) {
        if (time == null || sessionStart == null || sessionEnd == null) {
            return false;
        }
        return !time.isBefore(sessionStart) && time.isBefore(sessionEnd);
    }

    @Override
    protected String displayLabel() {
        return specialization == null ? fullName : fullName + " - " + specialization;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int id;
        private Integer userId;
        private String fullName;
        private String specialization;
        private String phone;
        private String email;
        private LocalTime sessionStart = LocalTime.of(8, 0);
        private LocalTime sessionEnd = LocalTime.of(20, 0);
        private boolean active = true;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder userId(Integer userId) {
            this.userId = userId;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder specialization(String specialization) {
            this.specialization = specialization;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder sessionStart(LocalTime sessionStart) {
            this.sessionStart = sessionStart;
            return this;
        }

        public Builder sessionEnd(LocalTime sessionEnd) {
            this.sessionEnd = sessionEnd;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Dentist build() {
            return new Dentist(this);
        }
    }
}
