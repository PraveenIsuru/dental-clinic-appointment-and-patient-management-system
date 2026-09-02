package lk.dentalclinic.model;

/**
 * Groups treatments so that PricingStrategyFactory (M4) can resolve one strategy
 * per family rather than one per treatment row. Adding a treatment to an existing
 * family therefore needs no new Java code at all.
 */
public enum TreatmentFamily {
    CONSULTATION,
    CLEANING,
    FILLING,
    EXTRACTION,
    ROOT_CANAL,
    PROSTHETIC,
    COSMETIC;

    public static TreatmentFamily of(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
