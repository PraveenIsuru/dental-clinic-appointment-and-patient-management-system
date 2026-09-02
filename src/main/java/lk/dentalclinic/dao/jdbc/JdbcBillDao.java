package lk.dentalclinic.dao.jdbc;

import lk.dentalclinic.dao.BillDao;
import lk.dentalclinic.dao.RowMapper;
import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.model.AppointmentStatus;
import lk.dentalclinic.model.Bill;
import lk.dentalclinic.model.BillLineItem;
import lk.dentalclinic.model.BillStatus;
import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.model.TreatmentFamily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class JdbcBillDao extends AbstractJdbcDao implements BillDao {

    /**
     * A bill with everything a receipt needs: the appointment, its patient, dentist and
     * treatment. The receipt is printed and handed to the patient, so a missing name is
     * not a cosmetic defect — one query rather than four keeps it whole.
     */
    private static final String SELECT_DETAILED = """
            SELECT b.bill_id, b.bill_no, b.appointment_id, b.consultation_fee,
                   b.treatment_charge, b.discount_amount, b.tax_amount, b.total_amount,
                   b.status, b.issued_at, b.paid_at,
                   a.appointment_no, a.patient_id, a.dentist_id, a.treatment_id,
                   a.appointment_date, a.appointment_time, a.status AS appointment_status,
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
            FROM bills b
            JOIN appointments a ON a.appointment_id = b.appointment_id
            JOIN patients     p ON p.patient_id     = a.patient_id
            JOIN dentists     d ON d.dentist_id     = a.dentist_id
            JOIN treatments   t ON t.treatment_id   = a.treatment_id
            """;

    private static final RowMapper<Bill> DETAILED = rs -> Bill.builder()
            .billId(rs.getInt("bill_id"))
            .billNo(rs.getString("bill_no"))
            .consultationFee(rs.getBigDecimal("consultation_fee"))
            .treatmentCharge(rs.getBigDecimal("treatment_charge"))
            .discountAmount(rs.getBigDecimal("discount_amount"))
            .taxAmount(rs.getBigDecimal("tax_amount"))
            .totalAmount(rs.getBigDecimal("total_amount"))
            .status(BillStatus.of(rs.getString("status")))
            .issuedAt(localDateTime(rs, "issued_at"))
            .paidAt(localDateTime(rs, "paid_at"))
            .appointment(Appointment.builder()
                    .appointmentId(rs.getInt("appointment_id"))
                    .appointmentNo(rs.getString("appointment_no"))
                    .appointmentDate(localDate(rs, "appointment_date"))
                    .appointmentTime(localTime(rs, "appointment_time"))
                    .status(AppointmentStatus.of(rs.getString("appointment_status")))
                    .notes(rs.getString("notes"))
                    .createdAt(localDateTime(rs, "created_at"))
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
                    .build())
            .build();

    private static final RowMapper<BillLineItem> LINE_ITEM = rs -> new BillLineItem(
            rs.getInt("line_id"),
            rs.getString("description"),
            rs.getInt("quantity"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("line_total"));

    public JdbcBillDao(ConnectionPool pool) {
        super(pool);
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Optional<Bill> findByNumberDetailed(String billNo) {
        return queryOne(SELECT_DETAILED + " WHERE b.bill_no = ?", DETAILED, billNo.trim())
                .map(this::withLineItems);
    }

    @Override
    public Optional<Bill> findByAppointmentId(int appointmentId) {
        return queryOne(SELECT_DETAILED + " WHERE b.appointment_id = ?", DETAILED, appointmentId)
                .map(this::withLineItems);
    }

    @Override
    public boolean existsForAppointment(int appointmentId) {
        return queryOne("SELECT 1 AS present FROM bills WHERE appointment_id = ?",
                rs -> rs.getInt("present"), appointmentId).isPresent();
    }

    @Override
    public List<Bill> findByPatientDetailed(int patientId) {
        return query(SELECT_DETAILED + " WHERE a.patient_id = ? ORDER BY b.issued_at DESC",
                DETAILED, patientId);
    }

    @Override
    public List<Bill> findByDateDetailed(LocalDate issuedOn) {
        return query(SELECT_DETAILED + " WHERE DATE(b.issued_at) = ? ORDER BY b.issued_at DESC",
                DETAILED, issuedOn);
    }

    @Override
    public List<BillLineItem> findLineItems(int billId) {
        return query("""
                SELECT line_id, description, quantity, unit_price, line_total
                FROM bill_line_items WHERE bill_id = ? ORDER BY line_id
                """, LINE_ITEM, billId);
    }

    /** A list view needs no line items; a single bill always does. */
    private Bill withLineItems(Bill bill) {
        return Bill.builder()
                .billId(bill.getBillId())
                .billNo(bill.getBillNo())
                .appointmentId(bill.getAppointmentId())
                .consultationFee(bill.getConsultationFee())
                .treatmentCharge(bill.getTreatmentCharge())
                .discountAmount(bill.getDiscountAmount())
                .taxAmount(bill.getTaxAmount())
                .totalAmount(bill.getTotalAmount())
                .status(bill.getStatus())
                .issuedAt(bill.getIssuedAt())
                .paidAt(bill.getPaidAt())
                .appointment(bill.getAppointment())
                .lineItems(findLineItems(bill.getBillId()))
                .build();
    }

    // ------------------------------------------------------------------ writes

    @Override
    public String nextBillNo(int year) {
        int next = queryOne("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(bill_no, 10) AS UNSIGNED)), 0) + 1 AS next_no
                FROM bills
                WHERE bill_no LIKE ?
                """, rs -> rs.getInt("next_no"), "BIL-" + year + "-%").orElse(1);
        return String.format("BIL-%d-%04d", year, next);
    }

    @Override
    public int insert(Bill bill, int issuedByUserId) {
        // total_amount is omitted on purpose - trg_bill_before_insert computes it and
        // rejects a discount over 25%, so a forged total cannot be stored.
        int billId = insertReturningKey("""
                        INSERT INTO bills (bill_no, appointment_id, consultation_fee,
                                           treatment_charge, discount_amount, tax_amount,
                                           status, issued_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                bill.getBillNo(), bill.getAppointmentId(), bill.getConsultationFee(),
                bill.getTreatmentCharge(), bill.getDiscountAmount(), bill.getTaxAmount(),
                bill.getStatus(), issuedByUserId);

        for (BillLineItem item : bill.getLineItems()) {
            update("""
                            INSERT INTO bill_line_items
                                (bill_id, description, quantity, unit_price, line_total)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    billId, item.description(), item.quantity(),
                    item.unitPrice(), item.lineTotal());
        }
        return billId;
    }

    @Override
    public void markPaid(int billId) {
        update("UPDATE bills SET status = 'PAID', paid_at = NOW() WHERE bill_id = ?", billId);
    }

    @Override
    public void markVoid(int billId) {
        update("UPDATE bills SET status = 'VOID' WHERE bill_id = ?", billId);
    }
}
