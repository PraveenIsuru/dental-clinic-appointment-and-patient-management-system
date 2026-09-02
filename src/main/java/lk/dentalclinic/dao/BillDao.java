package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Bill;
import lk.icbt.dentalclinic.model.BillLineItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Bills and their line items — brief requirement 4.
 *
 * <p>Deferred from M2 for the same reason as {@link AppointmentDao}: the method set is
 * driven by what {@code BillingService} and the receipt view actually ask for.
 *
 * <p>There is no {@code update} and no {@code delete}. A bill is a financial record: it is
 * issued, paid or voided, and never edited. Correcting one means voiding it and issuing
 * another, which leaves both in the audit trail. Offering an update method would make the
 * wrong thing easy.
 */
public interface BillDao {

    Optional<Bill> findByNumberDetailed(String billNo);

    /** The bill for an appointment, if one has been issued. Enforced unique by the schema. */
    Optional<Bill> findByAppointmentId(int appointmentId);

    boolean existsForAppointment(int appointmentId);

    /** A patient's bills, most recent first, with their appointment and treatment. */
    List<Bill> findByPatientDetailed(int patientId);

    List<Bill> findByDateDetailed(LocalDate issuedOn);

    List<BillLineItem> findLineItems(int billId);

    /**
     * Allocates the next {@code BIL-<year>-<0000>}.
     *
     * <p>Read-then-increment, so it must run inside the same transaction as the insert
     * that consumes it. Unlike appointment numbers there is no stored procedure: billing
     * is far less concurrent than booking — one clerk at a counter, not many patients
     * booking at once — and the UNIQUE constraint on {@code bill_no} turns the remaining
     * race into a loud failure rather than a duplicate.
     */
    String nextBillNo(int year);

    /**
     * Inserts a bill and its line items.
     *
     * <p>{@code total_amount} is deliberately not sent: {@code trg_bill_before_insert}
     * computes it and rejects a discount above 25%. See A10.
     *
     * @return the generated bill id
     */
    int insert(Bill bill, int issuedByUserId);

    void markPaid(int billId);

    void markVoid(int billId);
}
