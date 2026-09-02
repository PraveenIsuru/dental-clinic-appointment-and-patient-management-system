package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.AppointmentDao;
import lk.icbt.dentalclinic.dao.DataAccessException;
import lk.icbt.dentalclinic.dao.RowMapper;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.AppointmentStatus;
import lk.icbt.dentalclinic.model.Dentist;
import lk.icbt.dentalclinic.model.Patient;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.model.TreatmentFamily;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public final class JdbcAppointmentDao extends AbstractJdbcDao implements AppointmentDao {

    /** The appointment alone — enough for availability checks and status changes. */
    private static final String SELECT_PLAIN = """
            SELECT a.appointment_id, a.appointment_no, a.patient_id, a.dentist_id,
                   a.treatment_id, a.appointment_date, a.appointment_time, a.status,
                   a.notes, a.created_at
            FROM appointments a
            """;

    /**
     * The appointment with its patient, dentist and treatment.
     *
     * <p>One query with three joins rather than one query plus three lookups per row.
     * A day view showing twenty appointments would otherwise issue sixty extra
     * round trips — the N+1 select problem, which is invisible on seeded data and
     * ruinous on real volumes.
     */
    private static final String SELECT_DETAILED = """
            SELECT a.appointment_id, a.appointment_no, a.patient_id, a.dentist_id,
                   a.treatment_id, a.appointment_date, a.appointment_time, a.status,
                   a.notes, a.created_at,
                   p.patient_no, p.user_id AS patient_user_id, p.full_name AS patient_name,
                   p.address AS patient_address, p.contact_number AS patient_contact,
                   p.email AS patient_email, p.date_of_birth AS patient_dob,
                   d.user_id AS dentist_user_id, d.full_name AS dentist_name,
                   d.specialization, d.phone AS dentist_phone, d.email AS dentist_email,
                   d.session_start, d.session_end, d.active AS dentist_active,
                   t.code AS treatment_code, t.name AS treatment_name, t.family,
                   t.description AS treatment_description, t.base_cost,
                   t.duration_minutes, t.active AS treatment_active
            FROM appointments a
            JOIN patients   p ON p.patient_id   = a.patient_id
            JOIN dentists   d ON d.dentist_id   = a.dentist_id
            JOIN treatments t ON t.treatment_id = a.treatment_id
            """;

    private static final RowMapper<Appointment> PLAIN = rs -> baseBuilder(rs).build();

    private static final RowMapper<Appointment> DETAILED = rs -> baseBuilder(rs)
            .patient(Patient.builder()
                    .id(rs.getInt("patient_id"))
                    .patientNo(rs.getString("patient_no"))
                    .userId(nullableInt(rs, "patient_user_id"))
                    .fullName(rs.getString("patient_name"))
                    .address(rs.getString("patient_address"))
                    .contactNumber(rs.getString("patient_contact"))
                    .email(rs.getString("patient_email"))
                    .dateOfBirth(localDate(rs, "patient_dob"))
                    .build())
            .dentist(Dentist.builder()
                    .id(rs.getInt("dentist_id"))
                    .userId(nullableInt(rs, "dentist_user_id"))
                    .fullName(rs.getString("dentist_name"))
                    .specialization(rs.getString("specialization"))
                    .phone(rs.getString("dentist_phone"))
                    .email(rs.getString("dentist_email"))
                    .sessionStart(localTime(rs, "session_start"))
                    .sessionEnd(localTime(rs, "session_end"))
                    .active(rs.getBoolean("dentist_active"))
                    .build())
            .treatment(new Treatment(
                    rs.getInt("treatment_id"),
                    rs.getString("treatment_code"),
                    rs.getString("treatment_name"),
                    TreatmentFamily.of(rs.getString("family")),
                    rs.getString("treatment_description"),
                    rs.getBigDecimal("base_cost"),
                    rs.getInt("duration_minutes"),
                    rs.getBoolean("treatment_active")))
            .build();

    /** The columns both mappers share. */
    private static Appointment.Builder baseBuilder(ResultSet rs) throws SQLException {
        return Appointment.builder()
                .appointmentId(rs.getInt("appointment_id"))
                .appointmentNo(rs.getString("appointment_no"))
                .patientId(rs.getInt("patient_id"))
                .dentistId(rs.getInt("dentist_id"))
                .treatmentId(rs.getInt("treatment_id"))
                .appointmentDate(localDate(rs, "appointment_date"))
                .appointmentTime(localTime(rs, "appointment_time"))
                .status(AppointmentStatus.of(rs.getString("status")))
                .notes(rs.getString("notes"))
                .createdAt(localDateTime(rs, "created_at"));
    }

    public JdbcAppointmentDao(ConnectionPool pool) {
        super(pool);
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Optional<Appointment> findByNumberDetailed(String appointmentNo) {
        // The column collation is utf8mb4_unicode_ci, so this is already
        // case-insensitive: "apt-2026-0001" finds APT-2026-0001, as requirement 3 asks.
        return queryOne(SELECT_DETAILED + " WHERE a.appointment_no = ?",
                DETAILED, appointmentNo.trim());
    }

    @Override
    public Optional<Appointment> findById(int appointmentId) {
        return queryOne(SELECT_PLAIN + " WHERE a.appointment_id = ?", PLAIN, appointmentId);
    }

    @Override
    public List<Appointment> findByPatientDetailed(int patientId) {
        return query(SELECT_DETAILED + """
                WHERE a.patient_id = ?
                ORDER BY a.appointment_date DESC, a.appointment_time DESC
                """, DETAILED, patientId);
    }

    @Override
    public List<Appointment> findByDentistAndDateDetailed(int dentistId, LocalDate date) {
        return query(SELECT_DETAILED + """
                WHERE a.dentist_id = ? AND a.appointment_date = ?
                ORDER BY a.appointment_time
                """, DETAILED, dentistId, date);
    }

    @Override
    public List<Appointment> findByDateDetailed(LocalDate date) {
        return query(SELECT_DETAILED + """
                WHERE a.appointment_date = ?
                ORDER BY a.appointment_time, d.full_name
                """, DETAILED, date);
    }

    @Override
    public List<Appointment> findUpcomingDetailed(int limit) {
        return query(SELECT_DETAILED + """
                WHERE a.appointment_date >= CURDATE() AND a.status IN ('BOOKED', 'CONFIRMED')
                ORDER BY a.appointment_date, a.appointment_time
                LIMIT ?
                """, DETAILED, limit);
    }

    @Override
    public List<Appointment> findUpcomingForPatientDetailed(int patientId, int limit) {
        return query(SELECT_DETAILED + """
                WHERE a.patient_id = ? AND a.appointment_date >= CURDATE()
                  AND a.status IN ('BOOKED', 'CONFIRMED')
                ORDER BY a.appointment_date, a.appointment_time
                LIMIT ?
                """, DETAILED, patientId, limit);
    }

    // ------------------------------------------------------- availability support

    @Override
    public List<LocalTime> bookedTimes(int dentistId, LocalDate date) {
        return query("""
                SELECT appointment_time FROM appointments
                WHERE dentist_id = ? AND appointment_date = ? AND status <> 'CANCELLED'
                ORDER BY appointment_time
                """, rs -> rs.getTime("appointment_time").toLocalTime(), dentistId, date);
    }

    @Override
    public Optional<Appointment> findActiveAt(int dentistId, LocalDate date, LocalTime time) {
        return queryOne(SELECT_PLAIN + """
                WHERE a.dentist_id = ? AND a.appointment_date = ? AND a.appointment_time = ?
                  AND a.status <> 'CANCELLED'
                """, PLAIN, dentistId, date, time);
    }

    // ------------------------------------------------------------------ writes

    @Override
    public String nextAppointmentNo(int year) {
        return withConnection(connection -> {
            try (CallableStatement call =
                         connection.prepareCall("{CALL sp_next_appointment_no(?, ?)}")) {
                call.setInt(1, year);
                call.registerOutParameter(2, java.sql.Types.VARCHAR);
                call.execute();
                String number = call.getString(2);
                if (number == null) {
                    throw new DataAccessException(
                            "sp_next_appointment_no returned no number for " + year);
                }
                return number;
            }
        });
    }

    @Override
    public int insert(Appointment appointment, int createdByUserId) {
        return insertReturningKey("""
                        INSERT INTO appointments
                            (appointment_no, patient_id, dentist_id, treatment_id,
                             appointment_date, appointment_time, status, notes, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                appointment.getAppointmentNo(), appointment.getPatientId(),
                appointment.getDentistId(), appointment.getTreatmentId(),
                appointment.getAppointmentDate(), appointment.getAppointmentTime(),
                appointment.getStatus(), appointment.getNotes(), createdByUserId);
    }

    @Override
    public void updateSchedule(int appointmentId, LocalDate date, LocalTime time) {
        update("""
                UPDATE appointments SET appointment_date = ?, appointment_time = ?
                WHERE appointment_id = ?
                """, date, time, appointmentId);
    }

    @Override
    public void updateStatus(int appointmentId, AppointmentStatus status) {
        update("UPDATE appointments SET status = ? WHERE appointment_id = ?",
                status, appointmentId);
    }

    // ------------------------------------------------------------------ counts

    @Override
    public long countByStatusOn(LocalDate date, AppointmentStatus status) {
        return queryOne("""
                SELECT COUNT(*) AS n FROM appointments
                WHERE appointment_date = ? AND status = ?
                """, rs -> rs.getLong("n"), date, status).orElse(0L);
    }

    @Override
    public long countUpcomingForPatient(int patientId) {
        return queryOne("""
                SELECT COUNT(*) AS n FROM appointments
                WHERE patient_id = ? AND appointment_date >= CURDATE()
                  AND status IN ('BOOKED', 'CONFIRMED')
                """, rs -> rs.getLong("n"), patientId).orElse(0L);
    }

    @Override
    public long countForDentistOn(int dentistId, LocalDate date) {
        return queryOne("""
                SELECT COUNT(*) AS n FROM appointments
                WHERE dentist_id = ? AND appointment_date = ? AND status <> 'CANCELLED'
                """, rs -> rs.getLong("n"), dentistId, date).orElse(0L);
    }
}
