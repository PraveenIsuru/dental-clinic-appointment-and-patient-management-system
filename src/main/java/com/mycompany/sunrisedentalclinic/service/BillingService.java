package com.mycompany.sunrisedentalclinic.service;

import java.math.BigDecimal;

public class BillingService {

    public BigDecimal calculateTotal(BigDecimal consultation, BigDecimal treatment) {
        if (consultation == null || treatment == null) {
            throw new IllegalArgumentException("Fees are required");
        }
        if (consultation.signum() < 0 || treatment.signum() < 0) {
            throw new IllegalArgumentException("Fees cannot be negative");
        }
        return consultation.add(treatment);
    }
}
