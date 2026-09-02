package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * STRATEGY — how one family of treatments is priced.
 *
 * <p>The flagship pattern of this project. Each treatment family charges differently:
 * a second filled surface is cheaper because the anaesthetic and the setup are already
 * paid for, whereas a second extracted tooth is not, because it is a separate procedure.
 * Encoding that as a {@code switch} inside {@code BillingService} would put six unrelated
 * commercial rules in one method and require editing it for every new family.
 *
 * <p>The measurable result: <strong>{@code BillingService} does not name a single
 * treatment type anywhere.</strong> Adding a family means adding one class and one line
 * to {@link PricingStrategyFactory}, and changing nothing else.
 *
 * <p><em>Evaluated honestly.</em> The cost is six small classes where a novice would
 * write one method, and a reader tracing a price has to find the right implementation
 * first. That indirection is worth paying here because the rules genuinely differ and
 * change independently — a clinic revises its root-canal pricing without touching
 * extractions. It would not be worth paying if every family simply charged the base cost.
 */
public interface TreatmentPricingStrategy {

    /** The family this strategy prices. The factory indexes on it. */
    TreatmentFamily appliesTo();

    /** A one-line description of the rule, shown on the receipt so the patient can see it. */
    String rule();

    /**
     * Prices the treatment.
     *
     * @return a non-negative amount, scaled to 2 decimal places
     */
    BigDecimal priceFor(Treatment treatment, PricingContext context);

    /** Rounds to rupees and cents, half-up, as a printed receipt must. */
    default BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
