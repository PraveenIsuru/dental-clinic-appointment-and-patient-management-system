package lk.icbt.dentalclinic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A bill for one completed appointment.
 *
 * <p>{@code totalAmount} is computed by the database trigger
 * {@code trg_bill_before_insert}, not taken from a request, so a forged total in a
 * direct API call is overwritten (A10). {@link #computedTotal()} exists so the
 * service can show the figure before the insert and so a test can assert that Java
 * and MySQL agree.
 */
public final class Bill {

    /** The cap of A10, enforced again by {@code trg_bill_before_insert}. */
    public static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("0.25");

    private final int billId;
    private final String billNo;
    private final int appointmentId;
    private final BigDecimal consultationFee;
    private final BigDecimal treatmentCharge;
    private final BigDecimal discountAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final BillStatus status;
    private final LocalDateTime issuedAt;
    private final LocalDateTime paidAt;
    private final List<BillLineItem> lineItems;

    private final Appointment appointment;

    private Bill(Builder builder) {
        this.billId = builder.billId;
        this.billNo = builder.billNo;
        this.appointmentId = builder.appointmentId;
        this.consultationFee = zeroIfNull(builder.consultationFee);
        this.treatmentCharge = zeroIfNull(builder.treatmentCharge);
        this.discountAmount = zeroIfNull(builder.discountAmount);
        this.taxAmount = zeroIfNull(builder.taxAmount);
        this.totalAmount = builder.totalAmount;
        this.status = builder.status == null ? BillStatus.ISSUED : builder.status;
        this.issuedAt = builder.issuedAt;
        this.paidAt = builder.paidAt;
        this.lineItems = List.copyOf(builder.lineItems);
        this.appointment = builder.appointment;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public int getBillId() {
        return billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public BigDecimal getTreatmentCharge() {
        return treatmentCharge;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    /** The total as stored by the database, or the computed value on an unsaved bill. */
    public BigDecimal getTotalAmount() {
        return totalAmount != null ? totalAmount : computedTotal();
    }

    public BillStatus getStatus() {
        return status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public List<BillLineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public BigDecimal subtotal() {
        return consultationFee.add(treatmentCharge).setScale(2, RoundingMode.HALF_UP);
    }

    /** The same arithmetic {@code trg_bill_before_insert} performs. */
    public BigDecimal computedTotal() {
        return subtotal().subtract(discountAmount).add(taxAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /** The largest discount A10 permits on this bill's subtotal. */
    public BigDecimal maxDiscount() {
        return subtotal().multiply(MAX_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isDiscountWithinCap() {
        return discountAmount.compareTo(maxDiscount()) <= 0;
    }

    public boolean isPaid() {
        return status == BillStatus.PAID;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int billId;
        private String billNo;
        private int appointmentId;
        private BigDecimal consultationFee;
        private BigDecimal treatmentCharge;
        private BigDecimal discountAmount;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private BillStatus status;
        private LocalDateTime issuedAt;
        private LocalDateTime paidAt;
        private final List<BillLineItem> lineItems = new ArrayList<>();
        private Appointment appointment;

        public Builder billId(int billId) {
            this.billId = billId;
            return this;
        }

        public Builder billNo(String billNo) {
            this.billNo = billNo;
            return this;
        }

        public Builder appointmentId(int appointmentId) {
            this.appointmentId = appointmentId;
            return this;
        }

        public Builder consultationFee(BigDecimal consultationFee) {
            this.consultationFee = consultationFee;
            return this;
        }

        public Builder treatmentCharge(BigDecimal treatmentCharge) {
            this.treatmentCharge = treatmentCharge;
            return this;
        }

        public Builder discountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
            return this;
        }

        public Builder taxAmount(BigDecimal taxAmount) {
            this.taxAmount = taxAmount;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder status(BillStatus status) {
            this.status = status;
            return this;
        }

        public Builder issuedAt(LocalDateTime issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder paidAt(LocalDateTime paidAt) {
            this.paidAt = paidAt;
            return this;
        }

        public Builder addLineItem(BillLineItem item) {
            this.lineItems.add(item);
            return this;
        }

        public Builder lineItems(List<BillLineItem> items) {
            this.lineItems.clear();
            this.lineItems.addAll(items);
            return this;
        }

        public Builder appointment(Appointment appointment) {
            this.appointment = appointment;
            if (appointment != null) {
                this.appointmentId = appointment.getAppointmentId();
            }
            return this;
        }

        public Bill build() {
            return new Bill(this);
        }
    }

    @Override
    public String toString() {
        return billNo + " " + getTotalAmount() + " (" + status + ")";
    }
}
