package lk.dentalclinic.service.pricing;

import lk.dentalclinic.model.TreatmentFamily;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * FACTORY METHOD — resolves the {@link TreatmentPricingStrategy} for a treatment family.
 *
 * <p>The other half of the Strategy pair. Together they are why {@code BillingService}
 * contains no treatment-specific branching: it asks the factory for a strategy and the
 * strategy for a price, and never learns which family it is dealing with.
 *
 * <p>Registration is by {@link TreatmentPricingStrategy#appliesTo()} rather than by a
 * hard-coded list of pairs, so a strategy declares its own family and cannot be
 * registered under the wrong one. An {@link EnumMap} rather than a {@code HashMap}: the
 * key is an enum, so lookup is an array index with no hashing at all.
 *
 * <p><strong>Adding a family is a two-line change</strong> — write the strategy class,
 * add it to the list passed in by {@code ServiceRegistry}. Nothing in the service, the
 * handlers or the database needs to know.
 *
 * <p><em>Evaluated.</em> A Spring application would inject {@code List<PricingStrategy>}
 * and build this map from the component scan, saving the one line of registration. What
 * is gained by doing it here is that the whole resolution mechanism is nine lines of
 * ordinary Java that a reader can follow, with no reflection and no bean lifecycle — and
 * a duplicate-family mistake is caught at startup by the check below, not at the counter.
 */
public final class PricingStrategyFactory {

    private final Map<TreatmentFamily, TreatmentPricingStrategy> byFamily =
            new EnumMap<>(TreatmentFamily.class);
    private final TreatmentPricingStrategy fallback;

    /**
     * @param strategies the family-specific strategies; each must declare a distinct family
     * @param fallback   used for any family none of them claims
     * @throws IllegalArgumentException if two strategies claim the same family — a silent
     *                                  overwrite would mean prices quietly changing depending
     *                                  on construction order
     */
    public PricingStrategyFactory(Collection<TreatmentPricingStrategy> strategies,
                                  TreatmentPricingStrategy fallback) {
        this.fallback = fallback;
        for (TreatmentPricingStrategy strategy : strategies) {
            TreatmentFamily family = strategy.appliesTo();
            if (family == null) {
                throw new IllegalArgumentException(strategy.getClass().getSimpleName()
                        + " declares no family; pass it as the fallback instead");
            }
            TreatmentPricingStrategy existing = byFamily.put(family, strategy);
            if (existing != null) {
                throw new IllegalArgumentException("Two strategies claim " + family + ": "
                        + existing.getClass().getSimpleName() + " and "
                        + strategy.getClass().getSimpleName());
            }
        }
    }

    /** The set the application runs with. */
    public static PricingStrategyFactory withDefaults() {
        return new PricingStrategyFactory(
                List.of(new CleaningPricingStrategy(),
                        new FillingPricingStrategy(),
                        new ExtractionPricingStrategy(),
                        new RootCanalPricingStrategy(),
                        new CosmeticPricingStrategy()),
                new StandardPricingStrategy());
    }

    /**
     * The strategy for a family, or the fallback when none is registered.
     *
     * <p>Never returns {@code null} and never throws for an unknown family — see
     * {@link StandardPricingStrategy} for why refusing to price would be the worse
     * failure.
     */
    public TreatmentPricingStrategy strategyFor(TreatmentFamily family) {
        if (family == null) {
            return fallback;
        }
        return byFamily.getOrDefault(family, fallback);
    }

    /** How many families have a strategy of their own. Used by the pattern documentation. */
    public int registeredCount() {
        return byFamily.size();
    }

    /** Families relying on the fallback, so the catalogue page can say so. */
    public List<TreatmentFamily> familiesUsingFallback() {
        return java.util.Arrays.stream(TreatmentFamily.values())
                .filter(family -> !byFamily.containsKey(family))
                .toList();
    }
}
