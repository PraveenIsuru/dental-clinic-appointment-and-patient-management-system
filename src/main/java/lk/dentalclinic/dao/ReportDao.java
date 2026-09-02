package lk.dentalclinic.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The four management reports — the grid's *"reports to facilitate decision-making"*.
 *
 * <p>Read-only and aggregate-only. Kept apart from the entity DAOs because these queries
 * answer questions about the business rather than fetching records: they group, they roll
 * up, and two of them delegate to the stored routines written in M1. Mixing them into
 * {@code AppointmentDao} would blur what that interface is for.
 *
 * <p><strong>Where the work happens.</strong> Revenue and workload are computed by
 * {@code sp_daily_revenue_report} and {@code vw_dentist_workload} in the database, not by
 * pulling rows into Java and summing them. That is the point of having them: the database
 * aggregates over an index without moving the data, and the report stays correct if
 * another client ever asks the same question.
 */
public interface ReportDao {

    /**
     * A day's operations — how the clinic actually ran.
     *
     * @param chairMinutesLost the scheduled treatment time of appointments that were
     *                         cancelled: the report line that turns cancellations from a
     *                         count into a cost
     */
    record DailyOperations(LocalDate date, long booked, long confirmed, long completed,
                           long cancelled, long chairMinutesBooked, long chairMinutesLost,
                           BigDecimal billed, BigDecimal collected) {

        public long total() {
            return booked + confirmed + completed + cancelled;
        }

        /** Percentage of the day's appointments that were seen through. */
        public BigDecimal completionRate() {
            long all = total();
            return all == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(completed * 100.0 / all)
                    .setScale(1, java.math.RoundingMode.HALF_UP);
        }

        public BigDecimal outstanding() {
            return billed.subtract(collected);
        }
    }

    /** One row of {@code sp_daily_revenue_report}, including its ROLLUP total. */
    record TreatmentRevenue(String treatment, long billsIssued, BigDecimal consultationFees,
                            BigDecimal treatmentCharges, BigDecimal discounts, BigDecimal tax,
                            BigDecimal totalBilled, BigDecimal totalCollected) {

        /** The procedure's WITH ROLLUP row, which the view renders as a footer. */
        public boolean isTotal() {
            return "ALL TREATMENTS".equals(treatment);
        }
    }

    /** One row of {@code vw_dentist_workload}. */
    record DentistWorkload(int dentistId, String fullName, String specialization,
                           long totalAppointments, long completed, long cancelled,
                           long upcoming, BigDecimal completionRatePct) {
    }

    /** One visit in a patient's history. */
    record PatientVisit(String appointmentNo, LocalDate date, String dentist, String treatment,
                        String status, String billNo, BigDecimal total, String billStatus) {

        public boolean hasBill() {
            return billNo != null;
        }
    }

    DailyOperations dailyOperations(LocalDate date);

    List<TreatmentRevenue> revenueByTreatment(LocalDate date);

    List<DentistWorkload> dentistWorkload();

    List<PatientVisit> patientVisitHistory(int patientId);
}
