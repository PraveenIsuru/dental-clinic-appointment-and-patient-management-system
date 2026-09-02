package lk.dentalclinic.service.pricing;

import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * Scaling and polishing: a flat fee, with a recall discount for established patients.
 *
 * <p>The clinic wants patients back every six months, and a returning patient costs less
 * to treat — the notes exist, the history is known, the appointment is shorter. Ten per
 * cent off from the third visit onward pays for itself in retained patients.
 */
public final class CleaningPricingStrategy implements TreatmentPricingStrategy {

    private static final BigDecimal RECALL_DISCOUNT = new BigDecimal("0.10");

    @Override
    public TreatmentFamily appliesTo() {
        return TreatmentFamily.CLEANING;
    }

    @Override
    public String rule() {
        return "Flat fee per visit, less 10% for returning patients";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        BigDecimal gross = treatment.getBaseCost()
                .multiply(BigDecimal.valueOf(context.quantity()));

        if (context.isReturningPatient()) {
            return money(gross.subtract(gross.multiply(RECALL_DISCOUNT)));
        }
        return money(gross);
    }
}
