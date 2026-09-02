package lk.icbt.dentalclinic.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A treatment the clinic offers.
 *
 * <p>Money is {@link BigDecimal}, never {@code double}. A binary floating-point type
 * cannot represent 0.10 exactly, so accumulating charges in {@code double} drifts by
 * fractions of a rupee and a printed receipt eventually fails to add up.
 */
public final class Treatment {

    private final int treatmentId;
    private final String code;
    private final String name;
    private final TreatmentFamily family;
    private final String description;
    private final BigDecimal baseCost;
    private final int durationMinutes;
    private final boolean active;

    public Treatment(int treatmentId, String code, String name, TreatmentFamily family,
                     String description, BigDecimal baseCost, int durationMinutes, boolean active) {
        this.treatmentId = treatmentId;
        this.code = code;
        this.name = Objects.requireNonNull(name, "name");
        this.family = Objects.requireNonNull(family, "family");
        this.description = description;
        this.baseCost = Objects.requireNonNull(baseCost, "baseCost");
        this.durationMinutes = durationMinutes;
        this.active = active;
    }

    public int getTreatmentId() {
        return treatmentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public TreatmentFamily getFamily() {
        return family;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBaseCost() {
        return baseCost;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return name;
    }
}
