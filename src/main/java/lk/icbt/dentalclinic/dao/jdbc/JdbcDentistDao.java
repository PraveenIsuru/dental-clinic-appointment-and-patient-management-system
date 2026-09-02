package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.DentistDao;
import lk.icbt.dentalclinic.dao.RowMapper;
import lk.icbt.dentalclinic.model.Dentist;

import java.util.List;
import java.util.Optional;

public final class JdbcDentistDao extends AbstractJdbcDao implements DentistDao {

    private static final String SELECT = """
            SELECT dentist_id, user_id, full_name, specialization, phone, email,
                   session_start, session_end, active
            FROM dentists
            """;

    private static final RowMapper<Dentist> MAPPER = rs -> Dentist.builder()
            .id(rs.getInt("dentist_id"))
            .userId(nullableInt(rs, "user_id"))
            .fullName(rs.getString("full_name"))
            .specialization(rs.getString("specialization"))
            .phone(rs.getString("phone"))
            .email(rs.getString("email"))
            .sessionStart(localTime(rs, "session_start"))
            .sessionEnd(localTime(rs, "session_end"))
            .active(rs.getBoolean("active"))
            .build();

    public JdbcDentistDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<Dentist> findById(int dentistId) {
        return queryOne(SELECT + " WHERE dentist_id = ?", MAPPER, dentistId);
    }

    @Override
    public Optional<Dentist> findByUserId(int userId) {
        return queryOne(SELECT + " WHERE user_id = ?", MAPPER, userId);
    }

    @Override
    public List<Dentist> findAll() {
        return query(SELECT + " ORDER BY full_name", MAPPER);
    }

    @Override
    public List<Dentist> findActive() {
        return query(SELECT + " WHERE active = TRUE ORDER BY full_name", MAPPER);
    }

    @Override
    public int create(Dentist dentist) {
        return insertReturningKey("""
                        INSERT INTO dentists
                            (user_id, full_name, specialization, phone, email,
                             session_start, session_end, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                dentist.getUserId(), dentist.getFullName(), dentist.getSpecialization(),
                dentist.getContactNumber(), dentist.getEmail(),
                dentist.getSessionStart(), dentist.getSessionEnd(), dentist.isActive());
    }

    @Override
    public void update(Dentist dentist) {
        update("""
                        UPDATE dentists
                        SET full_name = ?, specialization = ?, phone = ?, email = ?,
                            session_start = ?, session_end = ?
                        WHERE dentist_id = ?
                        """,
                dentist.getFullName(), dentist.getSpecialization(),
                dentist.getContactNumber(), dentist.getEmail(),
                dentist.getSessionStart(), dentist.getSessionEnd(), dentist.getId());
    }

    @Override
    public void setActive(int dentistId, boolean active) {
        update("UPDATE dentists SET active = ? WHERE dentist_id = ?", active, dentistId);
    }
}
