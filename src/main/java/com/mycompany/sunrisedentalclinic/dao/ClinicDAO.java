package com.mycompany.sunrisedentalclinic.dao;

import com.mycompany.sunrisedentalclinic.model.*;
import com.mycompany.sunrisedentalclinic.util.DBConnection;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

public class ClinicDAO {

    public List<Dentist> dentists() throws SQLException {
        List<Dentist> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection()) {
            ensureDentistAvailabilityColumns(c);
            try (PreparedStatement sync = c.prepareStatement(
                    "INSERT INTO dentists(full_name,specialization,phone,email) "
                    + "SELECT u.full_name,'General Dentistry','',u.email FROM users u "
                    + "WHERE u.role='DENTIST' AND u.status=1 "
                    + "AND NOT EXISTS (SELECT 1 FROM dentists d WHERE "
                    + "(u.email IS NOT NULL AND u.email<>'' AND d.email=u.email) "
                    + "OR d.full_name=u.full_name)")) {
                sync.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM dentists ORDER BY full_name");
                    ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Dentist(r.getInt("dentist_id"), r.getString("full_name"), r.getString("specialization"),
                        r.getString("phone"), r.getString("email"), r.getTime("start_time").toLocalTime(),
                        r.getTime("end_time").toLocalTime()));
            }
            }
        }
        return out;
    }

    public void addDentist(String name, String specialization, String phone, String email,
            LocalTime startTime, LocalTime endTime) throws SQLException {
        String sql = "INSERT INTO dentists(full_name,specialization,phone,email,start_time,end_time) VALUES(?,?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection()) {
            ensureDentistAvailabilityColumns(c);
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, name); p.setString(2, specialization); p.setString(3, phone); p.setString(4, email);
                p.setTime(5, Time.valueOf(startTime)); p.setTime(6, Time.valueOf(endTime)); p.executeUpdate();
            }
        }
    }

    public void updateDentist(int id, String name, String specialization, String phone, String email,
            LocalTime startTime, LocalTime endTime) throws SQLException {
        String sql = "UPDATE dentists SET full_name=?,specialization=?,phone=?,email=?,start_time=?,end_time=? WHERE dentist_id=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, name); p.setString(2, specialization); p.setString(3, phone); p.setString(4, email);
            p.setTime(5, Time.valueOf(startTime)); p.setTime(6, Time.valueOf(endTime)); p.setInt(7, id); p.executeUpdate();
        }
    }

    public void saveDentistForUser(String oldName, String oldEmail, String name, String email,
            String specialization) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            ensureDentistAvailabilityColumns(c);
            Integer dentistId = null;
            String lookup = "SELECT dentist_id FROM dentists WHERE full_name=? "
                    + "OR (?<>'' AND email=?) ORDER BY dentist_id LIMIT 1";
            try (PreparedStatement p = c.prepareStatement(lookup)) {
                p.setString(1, oldName == null ? name : oldName);
                String lookupEmail = oldEmail == null ? email : oldEmail;
                p.setString(2, lookupEmail == null ? "" : lookupEmail);
                p.setString(3, lookupEmail == null ? "" : lookupEmail);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) dentistId = r.getInt(1);
                }
            }
            if (dentistId == null) {
                addDentist(name, specialization, "", email, LocalTime.of(9, 0), LocalTime.of(17, 0));
            } else {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE dentists SET full_name=?,email=?,specialization=? WHERE dentist_id=?")) {
                    p.setString(1, name); p.setString(2, email); p.setString(3, specialization);
                    p.setInt(4, dentistId); p.executeUpdate();
                }
            }
        }
    }

    public void updateDentistAvailability(int dentistId, LocalTime startTime, LocalTime endTime)
            throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                "UPDATE dentists SET start_time=?,end_time=? WHERE dentist_id=?")) {
            p.setTime(1, Time.valueOf(startTime)); p.setTime(2, Time.valueOf(endTime));
            p.setInt(3, dentistId); p.executeUpdate();
        }
    }

    public boolean withinDentistHours(int dentistId, LocalTime time) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            ensureDentistAvailabilityColumns(c);
            try (PreparedStatement p = c.prepareStatement("SELECT start_time,end_time FROM dentists WHERE dentist_id=?")) {
                p.setInt(1, dentistId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) return false;
                    LocalTime start = r.getTime(1).toLocalTime();
                    LocalTime end = r.getTime(2).toLocalTime();
                    return !time.isBefore(start) && time.isBefore(end);
                }
            }
        }
    }

    private void ensureDentistAvailabilityColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "dentists", "start_time", "TIME NOT NULL DEFAULT '09:00:00'");
        addColumnIfMissing(connection, "dentists", "end_time", "TIME NOT NULL DEFAULT '17:00:00'");
    }

    public List<Treatment> treatments() throws SQLException {
        List<Treatment> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT * FROM treatments ORDER BY treatment_name"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Treatment(r.getInt("treatment_id"), r.getString("treatment_name"), r.getString("description"), r.getBigDecimal("cost")));
            }
        }
        return out;
    }

    public void addTreatment(String n, String d, BigDecimal cost) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO treatments(treatment_name,description,cost) VALUES(?,?,?)")) {
            p.setString(1, n);
            p.setString(2, d);
            p.setBigDecimal(3, cost);
            p.executeUpdate();
        }
    }

    public void updateTreatment(int id, String name, String description, BigDecimal cost) throws SQLException {
        String sql = "UPDATE treatments SET treatment_name=?,description=?,cost=? WHERE treatment_id=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, name);
            p.setString(2, description);
            p.setBigDecimal(3, cost);
            p.setInt(4, id);
            p.executeUpdate();
        }
    }

    public int treatmentAppointmentCount(int treatmentId) throws SQLException {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM appointments WHERE treatment_id=?")) {
            p.setInt(1, treatmentId);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        }
    }

    public void deleteTreatment(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM treatments WHERE treatment_id=?")) {
            p.setInt(1, id);
            if (p.executeUpdate() == 0) {
                throw new SQLException("Treatment was not found.");
            }
        }
    }

    public List<Patient> patients() throws SQLException {
        List<Patient> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection()) {
            ensurePatientGenderColumn(c);
            try (PreparedStatement p = c.prepareStatement("SELECT * FROM patients ORDER BY patient_id DESC"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Patient(r.getInt("patient_id"), r.getString("full_name"), r.getString("gender"), r.getString("address"), r.getString("contact"), r.getString("email")));
            }
            }
        }
        return out;
    }

    public Optional<Patient> findPatientByContactOrEmail(String contact, String email) throws SQLException {
        String normalizedContact = contact == null ? "" : contact.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        String sql = "SELECT patient_id,full_name,gender,address,contact,email FROM patients "
                + "WHERE contact=? OR (?<>'' AND LOWER(email)=LOWER(?)) "
                + "ORDER BY CASE WHEN contact=? THEN 0 ELSE 1 END, patient_id LIMIT 1";
        try (Connection c = DBConnection.getConnection()) {
            ensurePatientGenderColumn(c);
            try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, normalizedContact);
            p.setString(2, normalizedEmail);
            p.setString(3, normalizedEmail);
            p.setString(4, normalizedContact);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    return Optional.of(new Patient(r.getInt("patient_id"), r.getString("full_name"),
                            r.getString("gender"), r.getString("address"), r.getString("contact"), r.getString("email")));
                }
            }
            }
        }
        return Optional.empty();
    }

    public int addPatient(String n, String gender, String a, String contact, String email) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            ensurePatientGenderColumn(c);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO patients(full_name,gender,address,contact,email) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, n);
            p.setString(2, gender);
            p.setString(3, a);
            p.setString(4, contact);
            p.setString(5, email);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (r.next()) {
                    return r.getInt(1);
                }
            }
            }
        }
        throw new SQLException("Patient was not created");
    }

    public int addPatient(String n, String a, String contact, String email) throws SQLException {
        return addPatient(n, null, a, contact, email);
    }

    public void updatePatient(int id, String name, String gender, String address, String contact, String email) throws SQLException {
        String sql = "UPDATE patients SET full_name=?,gender=?,address=?,contact=?,email=? WHERE patient_id=?";
        try (Connection c = DBConnection.getConnection()) {
            ensurePatientGenderColumn(c);
            try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, name);
            p.setString(2, gender);
            p.setString(3, address);
            p.setString(4, contact);
            p.setString(5, email);
            p.setInt(6, id);
            p.executeUpdate();
            }
        }
    }

    public int patientAppointmentCount(int patientId) throws SQLException {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM appointments WHERE patient_id=?")) {
            p.setInt(1, patientId);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        }
    }

    public void deletePatient(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement bills = c.prepareStatement(
                    "DELETE FROM bills WHERE appointment_id IN (SELECT appointment_id FROM appointments WHERE patient_id=?)");
                    PreparedStatement appointments = c.prepareStatement(
                            "DELETE FROM appointments WHERE patient_id=?");
                    PreparedStatement patient = c.prepareStatement(
                            "DELETE FROM patients WHERE patient_id=?")) {
                bills.setInt(1, id);
                bills.executeUpdate();
                appointments.setInt(1, id);
                appointments.executeUpdate();
                patient.setInt(1, id);
                if (patient.executeUpdate() == 0) {
                    throw new SQLException("Patient was not found.");
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public boolean slotAvailable(int dentistId, LocalDate date, LocalTime time) throws SQLException {
        String sql = "SELECT COUNT(*) FROM appointments WHERE dentist_id=? AND appointment_date=? AND appointment_time=? AND status<>'CANCELLED'";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, dentistId);
            p.setDate(2, java.sql.Date.valueOf(date));
            p.setTime(3, Time.valueOf(time));
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1) == 0;
            }
        }
    }

    public boolean appointmentNumberExists(String appointmentNo, Integer excludedAppointmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM appointments WHERE LOWER(appointment_no)=LOWER(?)"
                + (excludedAppointmentId == null ? "" : " AND appointment_id<>?");
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, appointmentNo == null ? "" : appointmentNo.trim());
            if (excludedAppointmentId != null) {
                p.setInt(2, excludedAppointmentId);
            }
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1) > 0;
            }
        }
    }

    public String nextAppointmentNumber() throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(appointment_no AS UNSIGNED)),0)+1 FROM appointments";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql);
                ResultSet r = p.executeQuery()) {
            r.next();
            return String.valueOf(r.getInt(1));
        }
    }

    public void changeAppointmentNumber(int appointmentId, String currentNumber, String requestedNumber)
            throws SQLException {
        if (currentNumber.equalsIgnoreCase(requestedNumber)) {
            return;
        }
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            String temporary = "TMP-" + appointmentId + "-" + System.nanoTime();
            try (PreparedStatement find = c.prepareStatement(
                    "SELECT appointment_id FROM appointments WHERE LOWER(appointment_no)=LOWER(?) AND appointment_id<>?");
                    PreparedStatement moveCurrent = c.prepareStatement(
                            "UPDATE appointments SET appointment_no=? WHERE appointment_id=?");
                    PreparedStatement moveOther = c.prepareStatement(
                            "UPDATE appointments SET appointment_no=? WHERE appointment_id=?")) {
                find.setString(1, requestedNumber);
                find.setInt(2, appointmentId);
                Integer otherId = null;
                try (ResultSet r = find.executeQuery()) {
                    if (r.next()) {
                        otherId = r.getInt(1);
                    }
                }
                moveCurrent.setString(1, temporary);
                moveCurrent.setInt(2, appointmentId);
                moveCurrent.executeUpdate();
                if (otherId != null) {
                    moveOther.setString(1, currentNumber);
                    moveOther.setInt(2, otherId);
                    moveOther.executeUpdate();
                }
                moveCurrent.setString(1, requestedNumber);
                moveCurrent.setInt(2, appointmentId);
                moveCurrent.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void addAppointment(String no, int patientId, int dentistId, int treatmentId, LocalDate date, LocalTime time, String notes) throws SQLException {
        String sql = "INSERT INTO appointments(appointment_no,patient_id,dentist_id,treatment_id,appointment_date,appointment_time,notes) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, no);
            p.setInt(2, patientId);
            p.setInt(3, dentistId);
            p.setInt(4, treatmentId);
            p.setDate(5, java.sql.Date.valueOf(date));
            p.setTime(6, Time.valueOf(time));
            p.setString(7, notes);
            p.executeUpdate();
        }
    }

    public boolean slotAvailableForUpdate(int appointmentId, int dentistId, LocalDate date, LocalTime time) throws SQLException {
        String sql = "SELECT COUNT(*) FROM appointments WHERE dentist_id=? AND appointment_date=? AND appointment_time=? AND status<>'CANCELLED' AND appointment_id<>?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, dentistId);
            p.setDate(2, java.sql.Date.valueOf(date));
            p.setTime(3, Time.valueOf(time));
            p.setInt(4, appointmentId);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1) == 0;
            }
        }
    }

    public void updateAppointment(int id, String no, int dentistId, int treatmentId,
            LocalDate date, LocalTime time, String status, String notes) throws SQLException {
        String sql = "UPDATE appointments SET appointment_no=?,dentist_id=?,treatment_id=?,appointment_date=?,appointment_time=?,status=?,notes=? WHERE appointment_id=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, no);
            p.setInt(2, dentistId);
            p.setInt(3, treatmentId);
            p.setDate(4, java.sql.Date.valueOf(date));
            p.setTime(5, Time.valueOf(time));
            p.setString(6, status);
            p.setString(7, notes);
            p.setInt(8, id);
            p.executeUpdate();
        }
    }

    public List<Appointment> appointments(String search) throws SQLException {
        String sql = "SELECT a.appointment_id,a.appointment_no,a.patient_id,p.full_name patient_name,a.dentist_id,d.full_name dentist_name,a.treatment_id,t.treatment_name,a.appointment_date,a.appointment_time,a.status,a.notes,CASE WHEN b.payment_status='PAID' THEN 'COMPLETED' ELSE 'PENDING' END payment_state FROM appointments a JOIN patients p ON p.patient_id=a.patient_id JOIN dentists d ON d.dentist_id=a.dentist_id JOIN treatments t ON t.treatment_id=a.treatment_id LEFT JOIN bills b ON b.appointment_id=a.appointment_id WHERE (?='' OR a.appointment_no LIKE CONCAT('%',?,'%') OR LOWER(p.full_name) LIKE CONCAT('%',LOWER(?),'%')) ORDER BY a.appointment_no ASC";
        List<Appointment> out = new ArrayList<>();
        String s = search == null ? "" : search.trim();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, s);
            p.setString(2, s);
            p.setString(3, s);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add(new Appointment(r.getInt("appointment_id"), r.getString("appointment_no"), r.getInt("patient_id"), r.getString("patient_name"), r.getInt("dentist_id"), r.getString("dentist_name"), r.getInt("treatment_id"), r.getString("treatment_name"), r.getDate("appointment_date").toLocalDate(), r.getTime("appointment_time").toLocalTime(), r.getString("status"), r.getString("notes"), r.getString("payment_state")));
                }
            }
        }
        return out;
    }

    public List<Appointment> appointmentsForPatient(int patientId) throws SQLException {
        String sql = "SELECT a.appointment_id,a.appointment_no,a.patient_id,p.full_name patient_name,"
                + "a.dentist_id,d.full_name dentist_name,a.treatment_id,t.treatment_name,"
                + "a.appointment_date,a.appointment_time,a.status,a.notes,"
                + "CASE WHEN b.payment_status='PAID' THEN 'COMPLETED' ELSE 'PENDING' END payment_state "
                + "FROM appointments a "
                + "JOIN patients p ON p.patient_id=a.patient_id "
                + "JOIN dentists d ON d.dentist_id=a.dentist_id "
                + "JOIN treatments t ON t.treatment_id=a.treatment_id "
                + "LEFT JOIN bills b ON b.appointment_id=a.appointment_id "
                + "WHERE a.patient_id=? "
                + "ORDER BY a.appointment_date DESC,a.appointment_time DESC";
        List<Appointment> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, patientId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add(new Appointment(r.getInt("appointment_id"), r.getString("appointment_no"),
                            r.getInt("patient_id"), r.getString("patient_name"), r.getInt("dentist_id"),
                            r.getString("dentist_name"), r.getInt("treatment_id"),
                            r.getString("treatment_name"), r.getDate("appointment_date").toLocalDate(),
                            r.getTime("appointment_time").toLocalTime(), r.getString("status"),
                            r.getString("notes"), r.getString("payment_state")));
                }
            }
        }
        return out;
    }

    public List<Appointment> unpaidAppointments() throws SQLException {
        String sql = "SELECT a.appointment_id,a.appointment_no,a.patient_id,p.full_name patient_name,"
                + "a.dentist_id,d.full_name dentist_name,a.treatment_id,t.treatment_name,"
                + "a.appointment_date,a.appointment_time,a.status,a.notes,'PENDING' payment_state "
                + "FROM appointments a "
                + "JOIN patients p ON p.patient_id=a.patient_id "
                + "JOIN dentists d ON d.dentist_id=a.dentist_id "
                + "JOIN treatments t ON t.treatment_id=a.treatment_id "
                + "WHERE a.status<>'CANCELLED' AND NOT EXISTS ("
                + "SELECT 1 FROM bills b WHERE b.appointment_id=a.appointment_id AND b.payment_status='PAID') "
                + "ORDER BY a.appointment_date DESC,a.appointment_time DESC";
        List<Appointment> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql);
                ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Appointment(r.getInt("appointment_id"), r.getString("appointment_no"),
                        r.getInt("patient_id"), r.getString("patient_name"), r.getInt("dentist_id"),
                        r.getString("dentist_name"), r.getInt("treatment_id"), r.getString("treatment_name"),
                        r.getDate("appointment_date").toLocalDate(), r.getTime("appointment_time").toLocalTime(),
                        r.getString("status"), r.getString("notes"), r.getString("payment_state")));
            }
        }
        return out;
    }

    public void deleteAppointment(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement bill = c.prepareStatement("DELETE FROM bills WHERE appointment_id=?");
                    PreparedStatement appointment = c.prepareStatement("DELETE FROM appointments WHERE appointment_id=?")) {
                bill.setInt(1, id);
                bill.executeUpdate();
                appointment.setInt(1, id);
                appointment.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public BigDecimal treatmentCost(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT cost FROM treatments WHERE treatment_id=?")) {
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    return r.getBigDecimal(1);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    public int saveBill(int appointmentId, BigDecimal consultation, BigDecimal treatment,
            BigDecimal total, String paymentMethod, String cardLast4) throws SQLException {
        String sql = "INSERT INTO bills(appointment_id,consultation_fee,treatment_charge,total_amount,payment_method,payment_status,card_last4,paid_at) "
                + "VALUES(?,?,?,?,?,'PAID',?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE "
                + "consultation_fee=VALUES(consultation_fee),treatment_charge=VALUES(treatment_charge),"
                + "total_amount=VALUES(total_amount),payment_method=VALUES(payment_method),"
                + "payment_status='PAID',card_last4=VALUES(card_last4),paid_at=CURRENT_TIMESTAMP";
        try (Connection c = DBConnection.getConnection()) {
            ensurePaymentColumns(c);
            try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, appointmentId);
            p.setBigDecimal(2, consultation);
            p.setBigDecimal(3, treatment);
            p.setBigDecimal(4, total);
            p.setString(5, paymentMethod);
            p.setString(6, cardLast4);
            p.executeUpdate();
            }
        }
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT bill_id FROM bills WHERE appointment_id=?")) {
            p.setInt(1, appointmentId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    return r.getInt(1);
                }
            }
        }
        return 0;
    }

    private void ensurePaymentColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "payment_method", "VARCHAR(10) NOT NULL DEFAULT 'CASH'");
        addColumnIfMissing(connection, "payment_status", "VARCHAR(20) NOT NULL DEFAULT 'PAID'");
        addColumnIfMissing(connection, "card_last4", "VARCHAR(4) NULL");
        addColumnIfMissing(connection, "paid_at", "TIMESTAMP NULL");
    }

    private void ensurePatientGenderColumn(Connection connection) throws SQLException {
        boolean exists;
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, "patients", "gender")) {
            exists = columns.next();
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE patients ADD COLUMN gender VARCHAR(20) NULL AFTER full_name");
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        addColumnIfMissing(connection, "bills", column, definition);
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        boolean exists;
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            exists = columns.next();
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    public List<String[]> helpTopics() throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT topic_id,title,content FROM help_topics ORDER BY title"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new String[]{r.getString(1), r.getString(2), r.getString(3)});
            }
        }
        return out;
    }

    public void addHelp(String t, String content) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO help_topics(title,content) VALUES(?,?)")) {
            p.setString(1, t);
            p.setString(2, content);
            p.executeUpdate();
        }
    }

    public boolean helpTitleExists(String title) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM help_topics WHERE LOWER(TRIM(title))=LOWER(TRIM(?))")) {
            p.setString(1, title);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getInt(1) > 0;
            }
        }
    }

    public void deleteHelp(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM help_topics WHERE topic_id=?")) {
            p.setInt(1, id);
            p.executeUpdate();
        }
    }

    public void createTicket(int uid, String subject, String desc, String priority) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO support_tickets(created_by,subject,description,priority) VALUES(?,?,?,?)")) {
            p.setInt(1, uid);
            p.setString(2, subject);
            p.setString(3, desc);
            p.setString(4, priority);
            p.executeUpdate();
        }
    }

    public List<SupportTicket> tickets(Integer uid) throws SQLException {
        String sql = "SELECT t.*,u.full_name creator FROM support_tickets t JOIN users u ON u.user_id=t.created_by WHERE (? IS NULL OR t.created_by=?) ORDER BY t.ticket_id DESC";
        List<SupportTicket> out = new ArrayList<>();
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (uid == null) {
                p.setNull(1, Types.INTEGER);
                p.setNull(2, Types.INTEGER);
            } else {
                p.setInt(1, uid);
                p.setInt(2, uid);
            }
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add(new SupportTicket(r.getInt("ticket_id"), r.getInt("created_by"), r.getString("creator"), r.getString("subject"), r.getString("description"), r.getString("priority"), r.getString("status"), r.getString("admin_response"), r.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return out;
    }

    public void updateTicket(int id, String status, String response) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE support_tickets SET status=?,admin_response=? WHERE ticket_id=?")) {
            p.setString(1, status);
            p.setString(2, response);
            p.setInt(3, id);
            p.executeUpdate();
        }
    }

    public void editTicket(int id, String subject, String description, String priority,
            String status, String response) throws SQLException {
        String sql = "UPDATE support_tickets SET subject=?,description=?,priority=?,status=?,admin_response=? WHERE ticket_id=?";
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, subject);
            p.setString(2, description);
            p.setString(3, priority);
            p.setString(4, status);
            p.setString(5, response);
            p.setInt(6, id);
            p.executeUpdate();
        }
    }

    public void deleteTicket(int id) throws SQLException {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement p = c.prepareStatement("DELETE FROM support_tickets WHERE ticket_id=?")) {
            p.setInt(1, id);
            if (p.executeUpdate() == 0) {
                throw new SQLException("Support ticket was not found.");
            }
        }
    }

    public Map<String, Integer> reportCounts() throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<>();
        String[] names = {"Patients", "Appointments", "Bills", "Open Tickets"};
        String[] qs = {"SELECT COUNT(*) FROM patients", "SELECT COUNT(*) FROM appointments", "SELECT COUNT(*) FROM bills", "SELECT COUNT(*) FROM support_tickets WHERE status<>'CLOSED'"};
        try (Connection c = DBConnection.getConnection()) {
            for (int i = 0; i < qs.length; i++)try (PreparedStatement p = c.prepareStatement(qs[i]); ResultSet r = p.executeQuery()) {
                r.next();
                m.put(names[i], r.getInt(1));
            }
        }
        return m;
    }

    public BigDecimal totalRevenue() throws SQLException {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement p = c.prepareStatement("SELECT COALESCE(SUM(total_amount),0) FROM bills");
                ResultSet r = p.executeQuery()) {
            r.next();
            return r.getBigDecimal(1);
        }
    }
}
