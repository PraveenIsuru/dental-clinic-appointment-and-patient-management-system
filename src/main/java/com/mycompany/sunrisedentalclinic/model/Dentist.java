package com.mycompany.sunrisedentalclinic.model;

import java.time.LocalTime;

public record Dentist(int id, String fullName, String specialization, String phone, String email,
        LocalTime startTime, LocalTime endTime) {

    @Override
    public String toString() {
        return fullName;
    }

    public String availability() {
        return startTime + " - " + endTime;
    }
}
