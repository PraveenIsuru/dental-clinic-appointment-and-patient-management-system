package com.mycompany.sunrisedentalclinic.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Appointment(int id, String appointmentNo, int patientId, String patientName,
        int dentistId, String dentistName, int treatmentId, String treatmentName,
        LocalDate date, LocalTime time, String status, String notes, String paymentStatus) {

    @Override
    public String toString() {
        return appointmentNo + " - " + patientName;
    }
}
