package lk.dentalclinic.service.pricing;

import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * Extractions: full rate per tooth, with an out-of-hours surcharge.
 *
 * <p>Deliberately <em>not</em> tapered like fillings. Each tooth is a separate surgical
 * procedure with its own risk and its own instruments; the saving that justifies the
 * filling taper does not exist here. Two strategies that look similar but differ in this
 * one respect are the clearest illustration of why the rules were separated rather than
 * merged behind a shared "per unit" method.
 *
 * <p>Evening extractions carry 25% because they are usually emergencies that displace
 * scheduled work.
 */
public final class ExtractionPricingStrategy implements TreatmentPricingStrategy {

    private static final BigDecimal OUT_OF_HOURS_SURCHARGE = new BigDecimal("0.25");

    @Override
    public TreatmentFamily appliesTo() {
        return TreatmentFamily.EXTRACTION;
    }

    @Override
    public String rule() {
        return "Full rate per tooth, plus 25% for appointments from 18:00";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        BigDecimal gross = treatment.getBaseCost()
                .multiply(BigDecimal.valueOf(context.quantity()));

        if (context.outOfHours()) {
            return money(gross.add(gross.multiply(OUT_OF_HOURS_SURCHARGE)));
        }
        return money(gross);
    }
}
