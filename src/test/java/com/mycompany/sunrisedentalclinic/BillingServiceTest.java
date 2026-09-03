package com.mycompany.sunrisedentalclinic;

import com.mycompany.sunrisedentalclinic.service.BillingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest {

    private final BillingService billingService = new BillingService();

    @Test
    void calculateTotalShouldAddConsultationAndTreatment() {

        BigDecimal consultation = new BigDecimal("2500.00");
        BigDecimal treatment = new BigDecimal("5000.00");

        BigDecimal result =
                billingService.calculateTotal(
                        consultation,
                        treatment
                );

        assertEquals(
                new BigDecimal("7500.00"),
                result
        );
    }

    @Test
    void calculateTotalShouldWorkWithZeroConsultationFee() {

        BigDecimal result =
                billingService.calculateTotal(
                        BigDecimal.ZERO,
                        new BigDecimal("5000.00")
                );

        assertEquals(
                new BigDecimal("5000.00"),
                result
        );
    }

    @Test
    void calculateTotalShouldWorkWithZeroTreatmentCharge() {

        BigDecimal result =
                billingService.calculateTotal(
                        new BigDecimal("2500.00"),
                        BigDecimal.ZERO
                );

        assertEquals(
                new BigDecimal("2500.00"),
                result
        );
    }

    @Test
    void negativeConsultationFeeShouldBeRejected() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> billingService.calculateTotal(
                                new BigDecimal("-500.00"),
                                new BigDecimal("5000.00")
                        )
                );

        assertEquals(
                "Fees cannot be negative",
                exception.getMessage()
        );
    }

    @Test
    void negativeTreatmentChargeShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> billingService.calculateTotal(
                        new BigDecimal("2500.00"),
                        new BigDecimal("-5000.00")
                )
        );
    }

    @Test
    void nullConsultationFeeShouldBeRejected() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> billingService.calculateTotal(
                                null,
                                new BigDecimal("5000.00")
                        )
                );

        assertEquals(
                "Fees are required",
                exception.getMessage()
        );
    }

    @Test
    void nullTreatmentChargeShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> billingService.calculateTotal(
                        new BigDecimal("2500.00"),
                        null
                )
        );
    }

    @Test
    void zeroValuesShouldReturnZero() {

        assertEquals(
                BigDecimal.ZERO,
                billingService.calculateTotal(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }
}