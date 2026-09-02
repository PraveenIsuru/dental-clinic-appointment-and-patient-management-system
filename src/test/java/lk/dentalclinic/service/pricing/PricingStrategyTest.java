package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic of each pricing rule.
 *
 * <p>These are the tests that make the Strategy pattern worth its indirection: each
 * family is a separate class precisely because each computes a different number, and
 * these assertions are what proves the difference is real rather than decorative.
 */
class PricingStrategyTest {

    private static Treatment treatment(TreatmentFamily family, String cost) {
        return new Treatment(1, family.name(), family.name() + " treatment", family,
                null, new BigDecimal(cost), 30, true);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual.toPlainString());
        assertEquals(2, actual.scale(), "money must be scaled to 2 decimal places");
    }

    @Nested
    @DisplayName("cleaning — flat fee with a recall discount")
    class Cleaning {

        private final CleaningPricingStrategy strategy = new CleaningPricingStrategy();
        private final Treatment cleaning = treatment(TreatmentFamily.CLEANING, "5000.00");

        @Test
        @DisplayName("a new patient pays the full fee")
        void fullFeeForNewPatient() {
            assertMoney("5000.00", strategy.priceFor(cleaning, PricingContext.single()));
        }

        @Test
        @DisplayName("a returning patient gets 10% off")
        void recallDiscount() {
            PricingContext returning = new PricingContext(1, false, 5);

            assertMoney("4500.00", strategy.priceFor(cleaning, returning));
        }

        @Test
        @DisplayName("the third visit is the boundary — two visits is still full price")
        void returningBoundary() {
            assertMoney("5000.00", strategy.priceFor(cleaning, new PricingContext(1, false, 2)));
            assertMoney("4500.00", strategy.priceFor(cleaning, new PricingContext(1, false, 3)));
        }
    }

    @Nested
    @DisplayName("filling — tapered per surface")
    class Filling {

        private final FillingPricingStrategy strategy = new FillingPricingStrategy();
        private final Treatment filling = treatment(TreatmentFamily.FILLING, "7500.00");

        @Test
        @DisplayName("one surface is the base cost")
        void singleSurface() {
            assertMoney("7500.00", strategy.priceFor(filling, PricingContext.single()));
        }

        @Test
        @DisplayName("each additional surface is charged at 70%")
        void additionalSurfacesTaper() {
            // 7500 + 5250 = 12750
            assertMoney("12750.00", strategy.priceFor(filling, new PricingContext(2, false, 0)));
            // 7500 + 5250 + 5250 = 18000, not 22500
            assertMoney("18000.00", strategy.priceFor(filling, new PricingContext(3, false, 0)));
        }

        @Test
        @DisplayName("the taper always undercharges relative to a flat multiple")
        void taperIsCheaperThanFlatRate() {
            BigDecimal tapered = strategy.priceFor(filling, new PricingContext(4, false, 0));
            BigDecimal flat = new BigDecimal("7500.00").multiply(BigDecimal.valueOf(4));

            assertTrue(tapered.compareTo(flat) < 0,
                    "the whole point of the taper is that it costs less");
        }
    }

    @Nested
    @DisplayName("extraction — full rate per tooth, with an evening surcharge")
    class Extraction {

        private final ExtractionPricingStrategy strategy = new ExtractionPricingStrategy();
        private final Treatment extraction = treatment(TreatmentFamily.EXTRACTION, "10000.00");

        @Test
        @DisplayName("each tooth is charged in full — deliberately not tapered")
        void noTaper() {
            assertMoney("10000.00", strategy.priceFor(extraction, PricingContext.single()));
            assertMoney("20000.00", strategy.priceFor(extraction, new PricingContext(2, false, 0)));
            assertMoney("30000.00", strategy.priceFor(extraction, new PricingContext(3, false, 0)));
        }

        @Test
        @DisplayName("an evening appointment carries 25%")
        void outOfHoursSurcharge() {
            PricingContext evening = new PricingContext(1, true, 0);

            assertMoney("12500.00", strategy.priceFor(extraction, evening));
        }

        @Test
        @DisplayName("the surcharge applies to the whole quantity")
        void surchargeAppliesToTotal() {
            assertMoney("25000.00", strategy.priceFor(extraction, new PricingContext(2, true, 0)));
        }

        @Test
        @DisplayName("18:00 is the boundary for the surcharge")
        void outOfHoursBoundary() {
            assertTrue(PricingContext.of(1, LocalTime.of(18, 0), 0).outOfHours());
            assertTrue(!PricingContext.of(1, LocalTime.of(17, 30), 0).outOfHours());
        }
    }

    @Nested
    @DisplayName("root canal — tapered per canal, more steeply than a filling")
    class RootCanal {

        private final RootCanalPricingStrategy strategy = new RootCanalPricingStrategy();
        private final Treatment rootCanal = treatment(TreatmentFamily.ROOT_CANAL, "25000.00");

        @Test
        @DisplayName("one canal is the base cost")
        void singleCanal() {
            assertMoney("25000.00", strategy.priceFor(rootCanal, PricingContext.single()));
        }

        @Test
        @DisplayName("a three-canal molar costs 55,000, not 75,000")
        void threeCanals() {
            // 25000 + 15000 + 15000
            assertMoney("55000.00", strategy.priceFor(rootCanal, new PricingContext(3, false, 0)));
        }

        @Test
        @DisplayName("the canal taper is steeper than the filling taper")
        void steeperThanFilling() {
            // Same base cost, so the rates are directly comparable.
            Treatment sameBase = treatment(TreatmentFamily.ROOT_CANAL, "10000.00");
            Treatment fillingBase = treatment(TreatmentFamily.FILLING, "10000.00");
            PricingContext two = new PricingContext(2, false, 0);

            BigDecimal canal = strategy.priceFor(sameBase, two);
            BigDecimal filling = new FillingPricingStrategy().priceFor(fillingBase, two);

            assertTrue(canal.compareTo(filling) < 0,
                    "more of the work is shared in endodontics, so the second unit costs less");
        }
    }

    @Nested
    @DisplayName("cosmetic and the fallback — flat per unit")
    class FlatRates {

        @Test
        @DisplayName("cosmetic work takes no discount and no surcharge")
        void cosmeticIsFlat() {
            CosmeticPricingStrategy strategy = new CosmeticPricingStrategy();
            Treatment whitening = treatment(TreatmentFamily.COSMETIC, "18000.00");

            assertMoney("18000.00", strategy.priceFor(whitening, PricingContext.single()));
            // Neither an evening slot nor a long history changes the price.
            assertMoney("18000.00", strategy.priceFor(whitening, new PricingContext(1, true, 9)));
            assertMoney("36000.00", strategy.priceFor(whitening, new PricingContext(2, true, 9)));
        }

        @Test
        @DisplayName("the fallback charges the base cost per unit")
        void fallbackIsBaseCost() {
            StandardPricingStrategy strategy = new StandardPricingStrategy();
            Treatment crown = treatment(TreatmentFamily.PROSTHETIC, "35000.00");

            assertMoney("35000.00", strategy.priceFor(crown, PricingContext.single()));
            assertMoney("70000.00", strategy.priceFor(crown, new PricingContext(2, false, 0)));
        }

        @Test
        @DisplayName("the fallback claims no family, which is how the factory recognises it")
        void fallbackClaimsNoFamily() {
            assertEquals(null, new StandardPricingStrategy().appliesTo());
        }
    }

    @Test
    @DisplayName("every strategy declares the family it prices, and a human-readable rule")
    void strategiesAreSelfDescribing() {
        var strategies = java.util.List.of(
                new CleaningPricingStrategy(), new FillingPricingStrategy(),
                new ExtractionPricingStrategy(), new RootCanalPricingStrategy(),
                new CosmeticPricingStrategy());

        for (TreatmentPricingStrategy strategy : strategies) {
            assertTrue(strategy.appliesTo() != null,
                    strategy.getClass().getSimpleName() + " must declare a family");
            assertTrue(strategy.rule() != null && !strategy.rule().isBlank(),
                    strategy.getClass().getSimpleName() + " must describe its rule for the receipt");
        }
    }

    @Test
    @DisplayName("a quantity below one is rejected rather than silently treated as one")
    void quantityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PricingContext(0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new PricingContext(-1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new PricingContext(1, false, -1));
    }
}
