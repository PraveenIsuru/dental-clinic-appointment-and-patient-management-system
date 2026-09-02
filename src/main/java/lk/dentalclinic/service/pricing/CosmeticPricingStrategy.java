package lk.dentalclinic.service.pricing;

import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.math.BigDecimal;

/**
 * Cosmetic work: full rate per session, no discounts and no surcharges.
 *
 * <p>Elective treatment, so there is no clinical case for a recall discount and no
 * emergency case for an out-of-hours premium — a patient choosing an evening whitening
 * appointment is not displacing urgent work, they are choosing a convenient time.
 *
 * <p>Worth keeping as a distinct class even though the arithmetic is the same as the
 * fallback: the <em>reason</em> is different, and if the clinic later introduces a
 * package rate for a course of whitening, the change belongs here and nowhere else.
 * A strategy whose current rule is simple is not the same as a strategy that is missing.
 */
public final class CosmeticPricingStrategy implements TreatmentPricingStrategy {

    @Override
    public TreatmentFamily appliesTo() {
        return TreatmentFamily.COSMETIC;
    }

    @Override
    public String rule() {
        return "Full rate per session; elective work carries no discount or surcharge";
    }

    @Override
    public BigDecimal priceFor(Treatment treatment, PricingContext context) {
        return money(treatment.getBaseCost().multiply(BigDecimal.valueOf(context.quantity())));
    }
}
