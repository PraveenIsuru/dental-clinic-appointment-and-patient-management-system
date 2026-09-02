package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * Root canal treatment, priced per canal.
 *
 * <p>The first canal carries the full fee; each additional canal in the same tooth is
 * 60%, since the access cavity, the rubber dam and the radiographs are shared. A molar
 * with three canals therefore costs 25,000 + 15,000 + 15,000 = 55,000 rather than 75,000.
 *
 * <p>The taper is steeper than the filling's 70% because more of the work is shared: the
 * difficult part of endodontics is opening the tooth, not the second canal.
 */
public final class RootCanalPricingStrategy implements TreatmentPricingStrategy {

    private static final BigDecimal ADDITIONAL_CANAL_RATE = new BigDecimal("0.60");

    @Override
    public TreatmentFamily appliesTo() {
        return TreatmentFamily.ROOT_CANAL;
    }

    @Override
    public String rule() {
        return "First canal at full rate, each additional canal at 60%";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        BigDecimal base = treatment.getBaseCost();
        BigDecimal additional = base.multiply(ADDITIONAL_CANAL_RATE)
                .multiply(BigDecimal.valueOf(context.quantity() - 1L));

        return money(base.add(additional));
    }
}
