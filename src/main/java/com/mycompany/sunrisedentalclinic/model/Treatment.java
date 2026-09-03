package com.mycompany.sunrisedentalclinic.model;

import java.math.BigDecimal;

public record Treatment(int id, String name, String description, BigDecimal cost) {

    @Override
    public String toString() {
        return name + " - Rs. " + cost;
    }
}
