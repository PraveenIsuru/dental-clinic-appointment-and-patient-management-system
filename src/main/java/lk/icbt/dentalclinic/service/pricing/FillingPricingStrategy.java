package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * Fillings, priced per surface with a tapered rate.
 *
 * <p>The first surface carries the full fee. Each additional surface treated in the same
 * visit is charged at 70%, because the anaesthetic, the isolation and the setup are
 * already done — only the restoration itself is repeated. Charging full price for every
 * surface would overcharge exactly the patients with the most work to do.
 *
 * <p>Three surfaces at a 7,500 base therefore cost 7,500 + 5,250 + 5,250 = 18,000, not
 * 22,500.
 */
public final class FillingPricingStrategy implements TreatmentPricingStrategy {

    private static final BigDecimal ADDITIONAL_SURFACE_RATE = new BigDecimal("0.70");

    @Override
    public TreatmentFamily appliesTo() {
        return TreatmentFamily.FILLING;
    }

    @Override
    public String rule() {
        return "First surface at full rate, each additional surface at 70%";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        BigDecimal base = treatment.getBaseCost();
        BigDecimal additional = base.multiply(ADDITIONAL_SURFACE_RATE)
                .multiply(BigDecimal.valueOf(context.quantity() - 1L));

        return money(base.add(additional));
    }
}
