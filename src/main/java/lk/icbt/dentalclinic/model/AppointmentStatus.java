package lk.icbt.dentalclinic.model;

/**
 * Appointment lifecycle: BOOKED -> CONFIRMED -> COMPLETED, with CANCELLED
 * reachable from either non-terminal state.
 */
public enum AppointmentStatus {
    BOOKED,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Only a completed appointment may be billed. */
    public boolean isBillable() {
        return this == COMPLETED;
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return switch (this) {
            case BOOKED    -> next == CONFIRMED || next == COMPLETED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public static AppointmentStatus of(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
