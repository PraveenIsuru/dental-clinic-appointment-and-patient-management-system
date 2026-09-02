package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.ReportDao;
import lk.icbt.dentalclinic.dao.RowMapper;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcReportDao extends AbstractJdbcDao implements ReportDao {

    public JdbcReportDao(ConnectionPool pool) {
        super(pool);
    }

    // -------------------------------------------------------- daily operations

    /**
     * Conditional aggregation: one pass over the day's rows produces every count, rather
     * than five separate {@code COUNT(*) WHERE status = …} queries. {@code SUM(condition)}
     * works because MySQL evaluates a boolean to 1 or 0.
     *
     * <p>{@code chair_minutes_lost} is the interesting column — the scheduled duration of
     * everything cancelled. It converts "seven cancellations" into "three and a half hours
     * of empty chair", which is the form a clinic manager can act on.
     */
    @Override
    public DailyOperations dailyOperations(LocalDate date) {
        return queryOne("""
                SELECT
                    SUM(a.status = 'BOOKED')                                      AS booked,
                    SUM(a.status = 'CONFIRMED')                                   AS confirmed,
                    SUM(a.status = 'COMPLETED')                                   AS completed,
                    SUM(a.status = 'CANCELLED')                                   AS cancelled,
                    COALESCE(SUM(CASE WHEN a.status <> 'CANCELLED'
                                      THEN t.duration_minutes END), 0)            AS minutes_booked,
                    COALESCE(SUM(CASE WHEN a.status =  'CANCELLED'
                                      THEN t.duration_minutes END), 0)            AS minutes_lost,
                    COALESCE((SELECT SUM(b.total_amount) FROM bills b
                              WHERE DATE(b.issued_at) = ? AND b.status <> 'VOID'), 0) AS billed,
                    COALESCE((SELECT SUM(b.total_amount) FROM bills b
                              WHERE DATE(b.issued_at) = ? AND b.status = 'PAID'), 0)  AS collected
                FROM appointments a
                JOIN treatments   t ON t.treatment_id = a.treatment_id
                WHERE a.appointment_date = ?
                """, rs -> new DailyOperations(
                        date,
                        rs.getLong("booked"),
                        rs.getLong("confirmed"),
                        rs.getLong("completed"),
                        rs.getLong("cancelled"),
                        rs.getLong("minutes_booked"),
                        rs.getLong("minutes_lost"),
                        rs.getBigDecimal("billed"),
                        rs.getBigDecimal("collected")),
                date, date, date)
                // An empty day still returns one row of NULLs from the aggregate, but a
                // day with no appointments at all should read as zeros, not as absent.
                .orElseGet(() -> new DailyOperations(date, 0, 0, 0, 0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO));
    }

    // ------------------------------------------------------ revenue by treatment

    /**
     * Delegates to {@code sp_daily_revenue_report}, one of the two stored procedures.
     *
     * <p>A {@link CallableStatement} rather than inlining the SQL: the grouping and the
     * {@code WITH ROLLUP} total live in the database, so the same report is available to
     * any client, and this method only maps the rows it returns.
     */
    @Override
    public List<TreatmentRevenue> revenueByTreatment(LocalDate date) {
        return withConnection(connection -> {
            try (CallableStatement call =
                         connection.prepareCall("{CALL sp_daily_revenue_report(?)}")) {
                call.setDate(1, java.sql.Date.valueOf(date));

                List<TreatmentRevenue> rows = new ArrayList<>();
                try (ResultSet rs = call.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new TreatmentRevenue(
                                rs.getString("treatment"),
                                rs.getLong("bills_issued"),
                                zeroIfNull(rs, "consultation_fees"),
                                zeroIfNull(rs, "treatment_charges"),
                                zeroIfNull(rs, "discounts"),
                                zeroIfNull(rs, "tax"),
                                zeroIfNull(rs, "total_billed"),
                                zeroIfNull(rs, "total_collected")));
                    }
                }
                return rows;
            }
        });
    }

    // ---------------------------------------------------------- dentist workload

    /** Reads {@code vw_dentist_workload}, the view written in M1. */
    @Override
    public List<DentistWorkload> dentistWorkload() {
        RowMapper<DentistWorkload> mapper = rs -> new DentistWorkload(
                rs.getInt("dentist_id"),
                rs.getString("full_name"),
                rs.getString("specialization"),
                rs.getLong("total_appointments"),
                rs.getLong("completed"),
                rs.getLong("cancelled"),
                rs.getLong("upcoming"),
                zeroIfNull(rs, "completion_rate_pct"));

        return query("""
                SELECT dentist_id, full_name, specialization, total_appointments,
                       completed, cancelled, upcoming, completion_rate_pct
                FROM vw_dentist_workload
                ORDER BY total_appointments DESC, full_name
                """, mapper);
    }

    // ------------------------------------------------------ patient visit history

    /**
     * A patient's visits with the bill for each, where one was issued.
     *
     * <p>LEFT JOIN, not JOIN: an appointment without a bill — cancelled, or completed but
     * not yet billed — must still appear. An inner join here would silently drop exactly
     * the rows the clinic most wants to notice.
     */
    @Override
    public List<PatientVisit> patientVisitHistory(int patientId) {
        RowMapper<PatientVisit> mapper = rs -> new PatientVisit(
                rs.getString("appointment_no"),
                localDate(rs, "appointment_date"),
                rs.getString("dentist"),
                rs.getString("treatment"),
                rs.getString("status"),
                rs.getString("bill_no"),
                rs.getBigDecimal("total_amount"),
                rs.getString("bill_status"));

        return query("""
                SELECT a.appointment_no, a.appointment_date, a.status,
                       d.full_name AS dentist, t.name AS treatment,
                       b.bill_no, b.total_amount, b.status AS bill_status
                FROM appointments a
                JOIN dentists   d ON d.dentist_id   = a.dentist_id
                JOIN treatments t ON t.treatment_id = a.treatment_id
                LEFT JOIN bills b ON b.appointment_id = a.appointment_id
                WHERE a.patient_id = ?
                ORDER BY a.appointment_date DESC, a.appointment_time DESC
                """, mapper, patientId);
    }

    private static BigDecimal zeroIfNull(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
