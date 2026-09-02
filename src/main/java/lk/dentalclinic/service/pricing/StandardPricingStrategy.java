package lk.dentalclinic.service.pricing;

import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * The fallback: base cost times quantity, nothing else.
 *
 * <p>Used for any family without a strategy of its own — currently
 * {@code CONSULTATION} and {@code PROSTHETIC}, and any family an administrator adds
 * through the treatments page in future.
 *
 * <p><strong>Why a fallback rather than an exception.</strong> An administrator can
 * create a treatment in a new family at any time, from a web form, with no developer
 * involved. If the factory threw for an unmapped family, that administrator's next
 * booking would be unbillable and the clinic would discover it at the counter with the
 * patient waiting. Charging the base cost is the answer a receptionist would give, and
 * it is right often enough to be safe. The alternative — refusing to bill — fails
 * closed in a way that helps nobody.
 *
 * <p>{@link #appliesTo()} returns {@code null}: this strategy claims no family, which is
 * how {@link PricingStrategyFactory} recognises it as the default rather than
 * registering it in the map.
 */
public final class StandardPricingStrategy implements TreatmentPricingStrategy {

    @Override
    public TreatmentFamily appliesTo() {
        return null;
    }

    @Override
    public String rule() {
        return "Standard rate: base cost per unit";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        return money(treatment.getBaseCost().multiply(BigDecimal.valueOf(context.quantity())));
    }
}
