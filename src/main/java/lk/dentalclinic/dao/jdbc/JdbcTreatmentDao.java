package lk.dentalclinic.dao.jdbc;

import lk.dentalclinic.dao.RowMapper;
import lk.dentalclinic.dao.TreatmentDao;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.util.List;
import java.util.Optional;

public final class JdbcTreatmentDao extends AbstractJdbcDao implements TreatmentDao {

    private static final String SELECT = """
            SELECT treatment_id, code, name, family, description,
                   base_cost, duration_minutes, active
            FROM treatments
            """;

    private static final RowMapper<Treatment> MAPPER = rs -> new Treatment(
            rs.getInt("treatment_id"),
            rs.getString("code"),
            rs.getString("name"),
            TreatmentFamily.of(rs.getString("family")),
            rs.getString("description"),
            rs.getBigDecimal("base_cost"),
            rs.getInt("duration_minutes"),
            rs.getBoolean("active"));

    public JdbcTreatmentDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<Treatment> findById(int treatmentId) {
        return queryOne(SELECT + " WHERE treatment_id = ?", MAPPER, treatmentId);
    }

    @Override
    public Optional<Treatment> findByCode(String code) {
        return queryOne(SELECT + " WHERE code = ?", MAPPER, code);
    }

    @Override
    public List<Treatment> findAll() {
        return query(SELECT + " ORDER BY name", MAPPER);
    }

    @Override
    public List<Treatment> findActive() {
        return query(SELECT + " WHERE active = TRUE ORDER BY name", MAPPER);
    }

    @Override
    public boolean existsByCode(String code) {
        return queryOne("SELECT 1 AS present FROM treatments WHERE code = ?",
                rs -> rs.getInt("present"), code).isPresent();
    }

    @Override
    public int create(Treatment treatment) {
        return insertReturningKey("""
                        INSERT INTO treatments
                            (code, name, family, description, base_cost, duration_minutes, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                treatment.getCode(), treatment.getName(), treatment.getFamily(),
                treatment.getDescription(), treatment.getBaseCost(),
                treatment.getDurationMinutes(), treatment.isActive());
    }

    @Override
    public void update(Treatment treatment) {
        update("""
                        UPDATE treatments
                        SET name = ?, family = ?, description = ?, base_cost = ?,
                            duration_minutes = ?
                        WHERE treatment_id = ?
                        """,
                treatment.getName(), treatment.getFamily(), treatment.getDescription(),
                treatment.getBaseCost(), treatment.getDurationMinutes(),
                treatment.getTreatmentId());
    }

    @Override
    public void setActive(int treatmentId, boolean active) {
        update("UPDATE treatments SET active = ? WHERE treatment_id = ?", active, treatmentId);
    }
}
