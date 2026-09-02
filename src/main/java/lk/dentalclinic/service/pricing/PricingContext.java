package lk.icbt.dentalclinic.service.pricing;

import java.time.LocalTime;

/**
 * The facts a pricing strategy may need beyond the treatment itself.
 *
 * <p>Passed as one object rather than as a growing parameter list, so adding a factor —
 * say a materials surcharge — changes this record and the one strategy that cares,
 * not every implementation's signature. That is what keeps the Strategy interface
 * stable as the pricing rules grow.
 *
 * @param quantity      how many units: surfaces filled, teeth removed, canals treated.
 *                      Always at least 1
 * @param outOfHours    whether the appointment falls outside normal hours, which some
 *                      families surcharge
 * @param previousVisits how many completed visits this patient already has, for the
 *                      families that reward returning patients
 */
public record PricingContext(int quantity, boolean outOfHours, int previousVisits) {

    /** Normal hours end at 18:00; later appointments carry a surcharge where applicable. */
    public static final LocalTime OUT_OF_HOURS_FROM = LocalTime.of(18, 0);

    public PricingContext {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1, was " + quantity);
        }
        if (previousVisits < 0) {
            throw new IllegalArgumentException(
                    "Previous visits cannot be negative, was " + previousVisits);
        }
    }

    /** A single unit, in hours, for a new patient — the common case. */
    public static PricingContext single() {
        return new PricingContext(1, false, 0);
    }

    public static PricingContext of(int quantity, LocalTime appointmentTime, int previousVisits) {
        boolean late = appointmentTime != null && !appointmentTime.isBefore(OUT_OF_HOURS_FROM);
        return new PricingContext(quantity, late, previousVisits);
    }

    /** Three or more completed visits marks an established patient. */
    public boolean isReturningPatient() {
        return previousVisits >= 3;
    }
}
