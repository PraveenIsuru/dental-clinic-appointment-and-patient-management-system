package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.RowMapper;
import lk.icbt.dentalclinic.model.Patient;

import java.util.List;
import java.util.Optional;

public final class JdbcPatientDao extends AbstractJdbcDao implements PatientDao {

    private static final String SELECT = """
            SELECT patient_id, patient_no, user_id, full_name, address,
                   contact_number, email, date_of_birth
            FROM patients
            """;

    private static final RowMapper<Patient> MAPPER = rs -> Patient.builder()
            .id(rs.getInt("patient_id"))
            .patientNo(rs.getString("patient_no"))
            .userId(nullableInt(rs, "user_id"))
            .fullName(rs.getString("full_name"))
            .address(rs.getString("address"))
            .contactNumber(rs.getString("contact_number"))
            .email(rs.getString("email"))
            .dateOfBirth(localDate(rs, "date_of_birth"))
            .build();

    public JdbcPatientDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<Patient> findById(int patientId) {
        return queryOne(SELECT + " WHERE patient_id = ?", MAPPER, patientId);
    }

    @Override
    public Optional<Patient> findByPatientNo(String patientNo) {
        return queryOne(SELECT + " WHERE patient_no = ?", MAPPER, patientNo);
    }

    @Override
    public Optional<Patient> findByUserId(int userId) {
        return queryOne(SELECT + " WHERE user_id = ?", MAPPER, userId);
    }

    @Override
    public List<Patient> findAll() {
        return query(SELECT + " ORDER BY full_name", MAPPER);
    }

    @Override
    public List<Patient> searchByNameOrContact(String term) {
        String pattern = "%" + term.trim() + "%";
        return query(SELECT + " WHERE full_name LIKE ? OR contact_number LIKE ? "
                + "OR patient_no LIKE ? ORDER BY full_name LIMIT 50",
                MAPPER, pattern, pattern, pattern);
    }

    @Override
    public int create(Patient patient) {
        return insertReturningKey("""
                        INSERT INTO patients
                            (patient_no, user_id, full_name, address, contact_number, email, date_of_birth)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                patient.getPatientNo(), patient.getUserId(), patient.getFullName(),
                patient.getAddress(), patient.getContactNumber(), patient.getEmail(),
                patient.getDateOfBirth());
    }

    @Override
    public void update(Patient patient) {
        update("""
                        UPDATE patients
                        SET full_name = ?, address = ?, contact_number = ?, email = ?, date_of_birth = ?
                        WHERE patient_id = ?
                        """,
                patient.getFullName(), patient.getAddress(), patient.getContactNumber(),
                patient.getEmail(), patient.getDateOfBirth(), patient.getId());
    }

    @Override
    public String nextPatientNo() {
        // Unlike the appointment number, this has no stored procedure: patient
        // registration is far less concurrent than booking, and the surrounding
        // transaction plus the UNIQUE constraint on patient_no make a collision
        // fail loudly rather than silently duplicate.
        int next = queryOne("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(patient_no, 5) AS UNSIGNED)), 0) + 1 AS next_no
                FROM patients
                WHERE patient_no REGEXP '^PAT-[0-9]+$'
                """, rs -> rs.getInt("next_no")).orElse(1);
        return String.format("PAT-%06d", next);
    }
}
