package lk.icbt.dentalclinic.service.pricing;

import lk.icbt.dentalclinic.model.TreatmentFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingStrategyFactoryTest {

    private final PricingStrategyFactory factory = PricingStrategyFactory.withDefaults();

    @Test
    @DisplayName("each family resolves to its own strategy")
    void resolvesEachFamily() {
        assertInstanceOf(CleaningPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.CLEANING));
        assertInstanceOf(FillingPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.FILLING));
        assertInstanceOf(ExtractionPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.EXTRACTION));
        assertInstanceOf(RootCanalPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.ROOT_CANAL));
        assertInstanceOf(CosmeticPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.COSMETIC));
    }

    @Test
    @DisplayName("an unmapped family falls back rather than failing")
    void unmappedFamilyUsesFallback() {
        // CONSULTATION and PROSTHETIC have no strategy of their own. An administrator
        // can create a treatment in either from the web form, and it must be billable.
        assertInstanceOf(StandardPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.CONSULTATION));
        assertInstanceOf(StandardPricingStrategy.class,
                factory.strategyFor(TreatmentFamily.PROSTHETIC));
    }

    @Test
    @DisplayName("a null family falls back instead of throwing")
    void nullFamilyUsesFallback() {
        assertInstanceOf(StandardPricingStrategy.class, factory.strategyFor(null));
    }

    @Test
    @DisplayName("every family in the enum resolves to something")
    void everyFamilyResolves() {
        // The guarantee BillingService depends on: there is no family it cannot price.
        for (TreatmentFamily family : TreatmentFamily.values()) {
            assertTrue(factory.strategyFor(family) != null, family + " must resolve");
        }
    }

    @Test
    @DisplayName("the factory reports which families rely on the fallback")
    void reportsFallbackFamilies() {
        assertEquals(5, factory.registeredCount());
        assertEquals(List.of(TreatmentFamily.CONSULTATION, TreatmentFamily.PROSTHETIC),
                factory.familiesUsingFallback());
    }

    @Test
    @DisplayName("two strategies claiming the same family is rejected at construction")
    void duplicateFamilyIsRejected() {
        // A silent overwrite would mean prices changing depending on list order — a bug
        // that would show up as a wrong number on a patient's receipt, with nothing in
        // the logs. Better to refuse to start.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new PricingStrategyFactory(
                        List.of(new CleaningPricingStrategy(), new CleaningPricingStrategy()),
                        new StandardPricingStrategy()));

        assertTrue(thrown.getMessage().contains("CLEANING"), thrown.getMessage());
    }

    @Test
    @DisplayName("passing the fallback in the strategy list is rejected")
    void fallbackInListIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new PricingStrategyFactory(
                        List.of(new StandardPricingStrategy()), new StandardPricingStrategy()));

        assertTrue(thrown.getMessage().contains("declares no family"), thrown.getMessage());
    }

    @Test
    @DisplayName("adding a family needs one class and one registration, nothing else")
    void addingAFamilyIsLocal() {
        // The claim the Strategy/Factory pair makes. A new strategy for a family that
        // previously fell back now wins, and no other code changed.
        TreatmentPricingStrategy custom = new TreatmentPricingStrategy() {
            @Override
            public TreatmentFamily appliesTo() {
                return TreatmentFamily.PROSTHETIC;
            }

            @Override
            public String rule() {
                return "Laboratory fee plus fitting";
            }

            @Override
            public java.math.BigDecimal priceFor(lk.icbt.dentalclinic.model.Treatment treatment,
                                                 PricingContext context) {
                return money(treatment.getBaseCost());
            }
        };

        PricingStrategyFactory extended = new PricingStrategyFactory(
                List.of(new CleaningPricingStrategy(), custom), new StandardPricingStrategy());

        assertSame(custom, extended.strategyFor(TreatmentFamily.PROSTHETIC));
        assertInstanceOf(StandardPricingStrategy.class,
                extended.strategyFor(TreatmentFamily.EXTRACTION));
    }
}
