package com.mycompany.sunrisedentalclinic.service;

import com.mycompany.sunrisedentalclinic.dao.ClinicDAO;
import com.mycompany.sunrisedentalclinic.model.Dentist;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class AppointmentService {

    private final ClinicDAO clinicDAO;

    public AppointmentService() {
        this(new ClinicDAO());
    }

    public AppointmentService(ClinicDAO clinicDAO) {
        this.clinicDAO = clinicDAO;
    }

    public void validateAppointment(
            int dentistId,
            LocalDate date,
            LocalTime time
    ) throws SQLException {

        if (dentistId <= 0) {
            throw new IllegalArgumentException(
                    "Dentist is required"
            );
        }

        if (date == null) {
            throw new IllegalArgumentException(
                    "Appointment date is required"
            );
        }

        if (time == null) {
            throw new IllegalArgumentException(
                    "Appointment time is required"
            );
        }

        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Appointment date cannot be in the past"
            );
        }

        if (!clinicDAO.withinDentistHours(
                dentistId,
                time
        )) {

            throw new IllegalArgumentException(
                    "Appointment is outside dentist working hours"
            );
        }

        if (!clinicDAO.slotAvailable(
                dentistId,
                date,
                time
        )) {

            throw new IllegalArgumentException(
                    "Dentist is already booked for this time"
            );
        }
    }

    public LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Time must use HH:mm format. Example: 14:30");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Time must use HH:mm format. Example: 14:30", exception);
        }
    }

    public void validateDentistHours(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Dentist start and end times are required.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be later than start time.");
        }
    }

    public void validateSlot(Dentist dentist, LocalDate date, LocalTime time, boolean slotAvailable) {
        if (dentist == null) {
            throw new IllegalArgumentException("Please select a dentist.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Please select a date.");
        }
        if (time == null) {
            throw new IllegalArgumentException("Please select an appointment time.");
        }
        if (dentist.startTime() == null || dentist.endTime() == null
                || time.isBefore(dentist.startTime()) || !time.isBefore(dentist.endTime())) {
            throw new IllegalArgumentException(
                    "Appointment time must be within the dentist's available hours: "
                    + dentist.availability());
        }
        if (!slotAvailable) {
            throw new IllegalArgumentException(
                    "The selected dentist is not available at this date and time.");
        }
    }

    public int validateAppointmentNumber(String value) {
        try {
            int number = Integer.parseInt(value == null ? "" : value.trim());
            if (number < 1) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Appointment number must be 1 or greater.", exception);
        }
    }
}
