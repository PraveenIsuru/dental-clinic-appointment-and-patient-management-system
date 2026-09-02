package lk.icbt.dentalclinic.service;

import lk.icbt.dentalclinic.dao.AppointmentDao;
import lk.icbt.dentalclinic.dao.BillDao;
import lk.icbt.dentalclinic.dao.BusinessRuleViolationException;
import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.SettingsDao;
import lk.icbt.dentalclinic.dao.jdbc.TransactionManager;
import lk.icbt.dentalclinic.event.BillIssuedEvent;
import lk.icbt.dentalclinic.event.EventBus;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.Bill;
import lk.icbt.dentalclinic.model.BillLineItem;
import lk.icbt.dentalclinic.model.BillStatus;
import lk.icbt.dentalclinic.model.RoleCode;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.pricing.PricingContext;
import lk.icbt.dentalclinic.service.pricing.PricingStrategyFactory;
import lk.icbt.dentalclinic.service.pricing.TreatmentPricingStrategy;
import lk.icbt.dentalclinic.validation.ValidationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

/**
 * Calculating and issuing bills — brief requirement 4.
 *
 * <p><strong>Read this class looking for a treatment name. There is not one.</strong> No
 * {@code if}, no {@code switch}, no string comparison on a treatment type anywhere. The
 * price comes from a strategy the factory chose, and this class never learns which. That
 * is the whole point of the Strategy/Factory pair, and it is the single clearest piece of
 * design-pattern evidence in the project.
 *
 * <p>Realises the Generate Bill sequence diagram, including its worked example:
 * consultation 2,500 + root canal 25,000 = 27,500 subtotal, less a 10% discount of 2,750,
 * giving 24,750.
 */
public final class BillingService {

    private static final Logger LOG = Logger.getLogger(BillingService.class.getName());

    private final BillDao billDao;
    private final AppointmentDao appointmentDao;
    private final PatientDao patientDao;
    private final SettingsDao settingsDao;
    private final PricingStrategyFactory pricingFactory;
    private final AppointmentAccessPolicy accessPolicy;
    private final TransactionManager transactions;
    private final EventBus eventBus;

    public BillingService(BillDao billDao, AppointmentDao appointmentDao, PatientDao patientDao,
                          SettingsDao settingsDao, PricingStrategyFactory pricingFactory,
                          AppointmentAccessPolicy accessPolicy, TransactionManager transactions,
                          EventBus eventBus) {
        this.billDao = billDao;
        this.appointmentDao = appointmentDao;
        this.patientDao = patientDao;
        this.settingsDao = settingsDao;
        this.pricingFactory = pricingFactory;
        this.accessPolicy = accessPolicy;
        this.transactions = transactions;
        this.eventBus = eventBus;
    }

    /** A priced bill that has not been saved, so the counter can show it before committing. */
    public record Quotation(Appointment appointment, BigDecimal consultationFee,
                            BigDecimal treatmentCharge, BigDecimal discountAmount,
                            BigDecimal taxAmount, BigDecimal total,
                            String pricingRule, List<BillLineItem> lineItems) {
    }

    // ------------------------------------------------------------------ quoting

    /**
     * Prices an appointment without saving anything.
     *
     * <p>Used by the billing form so the clerk sees the figure before committing, and by
     * the tests, which can assert the arithmetic without writing rows.
     */
    public Quotation quote(Appointment appointment, int quantity, BigDecimal discountPercent) {
        ClinicSettings settings = ClinicSettings.load(settingsDao);
        Treatment treatment = appointment.getTreatment();
        if (treatment == null) {
            throw new IllegalStateException("Appointment " + appointment.getAppointmentNo()
                    + " was loaded without its treatment; cannot price it");
        }

        int previousVisits = (int) appointmentDao.findByPatientDetailed(appointment.getPatientId())
                .stream()
                .filter(Appointment::isBillable)
                .count();

        PricingContext context = PricingContext.of(
                quantity, appointment.getAppointmentTime(), previousVisits);

        // FACTORY METHOD, then STRATEGY. Nothing below knows the treatment family.
        TreatmentPricingStrategy strategy = pricingFactory.strategyFor(treatment.getFamily());
        BigDecimal treatmentCharge = strategy.priceFor(treatment, context);

        BigDecimal consultationFee = settings.consultationFee();
        BigDecimal subtotal = consultationFee.add(treatmentCharge).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cappedPercent = discountPercent.min(settings.maxDiscountPercent());
        BigDecimal discount = subtotal.multiply(cappedPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = taxable.multiply(settings.taxRate()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = taxable.add(tax).setScale(2, RoundingMode.HALF_UP);

        List<BillLineItem> lines = List.of(
                BillLineItem.of("Consultation", 1, consultationFee),
                BillLineItem.of(lineDescription(treatment, quantity), 1, treatmentCharge));

        return new Quotation(appointment, consultationFee, treatmentCharge, discount, tax,
                total, strategy.rule(), lines);
    }

    private static String lineDescription(Treatment treatment, int quantity) {
        return quantity <= 1
                ? treatment.getName()
                : treatment.getName() + " (" + quantity + " units)";
    }

    // ------------------------------------------------------------------ issuing

    /**
     * Issues a bill for a completed appointment.
     *
     * @throws BillingNotAllowedException the appointment is not completed, or already billed
     * @throws ValidationException        the discount exceeds the cap
     */
    public Bill generate(String appointmentNo, int quantity, BigDecimal discountPercent,
                         Session actor) {
        Appointment appointment = appointmentDao.findByNumberDetailed(appointmentNo)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentNo));
        accessPolicy.requireView(appointment, actor);

        if (actor.hasRole(RoleCode.PATIENT)) {
            throw new BillingNotAllowedException("Only clinic staff can issue a bill.");
        }
        if (!appointment.isBillable()) {
            throw new BillingNotAllowedException(
                    "Only a completed appointment can be billed. " + appointmentNo
                            + " is " + appointment.getStatus() + ".");
        }
        if (billDao.existsForAppointment(appointment.getAppointmentId())) {
            throw new BillingNotAllowedException(
                    "A bill has already been issued for " + appointmentNo + ".");
        }

        ClinicSettings settings = ClinicSettings.load(settingsDao);
        if (discountPercent.compareTo(settings.maxDiscountPercent()) > 0) {
            // Checked here for a readable message; trg_bill_before_insert enforces it
            // again so the rule holds for a caller that never reaches this method.
            throw new ValidationException(ValidationResult.empty().reject("discountPercent",
                    "A discount may not exceed " + settings.maxDiscountPercent().stripTrailingZeros()
                            .toPlainString() + "% of the subtotal."));
        }
        if (discountPercent.signum() < 0) {
            throw new ValidationException(ValidationResult.empty()
                    .reject("discountPercent", "A discount cannot be negative."));
        }
        if (quantity < 1) {
            throw new ValidationException(ValidationResult.empty()
                    .reject("quantity", "Enter how many units were treated (at least 1)."));
        }

        Quotation quotation = quote(appointment, quantity, discountPercent);

        Bill saved;
        try {
            saved = transactions.inTransactionAs(actor.getUserId(), () -> {
                String billNo = billDao.nextBillNo(
                        appointment.getAppointmentDate().getYear());

                Bill bill = Bill.builder()
                        .billNo(billNo)
                        .appointmentId(appointment.getAppointmentId())
                        .consultationFee(quotation.consultationFee())
                        .treatmentCharge(quotation.treatmentCharge())
                        .discountAmount(quotation.discountAmount())
                        .taxAmount(quotation.taxAmount())
                        .status(BillStatus.ISSUED)
                        .appointment(appointment)
                        .lineItems(quotation.lineItems())
                        .build();

                int billId = billDao.insert(bill, actor.getUserId());
                return billDao.findByNumberDetailed(billNo).orElseThrow(
                        () -> new IllegalStateException("Bill " + billId + " vanished after insert"));
            });
        } catch (BusinessRuleViolationException e) {
            // trg_bill_before_insert refused it. The trigger's own message is already
            // user-facing, so it is carried through rather than replaced.
            throw new ValidationException(
                    ValidationResult.empty().reject("discountPercent", e.getMessage()));
        }

        LOG.info(() -> "Issued " + saved.getBillNo() + " for " + appointmentNo
                + " total " + saved.getTotalAmount() + " by " + actor.getUsername());

        // After commit, never inside: a failing listener must not roll back a bill that
        // has already been issued to the patient.
        eventBus.publish(BillIssuedEvent.of(saved, actor.getUsername()));
        return saved;
    }

    // ------------------------------------------------------------------ lookup

    public Bill findByNumber(String billNo, Session actor) {
        Bill bill = billDao.findByNumberDetailed(billNo)
                .orElseThrow(() -> new BillNotFoundException(billNo));

        // Record-level scoping, same rule as appointments (A6): a patient asking for
        // someone else's bill is told it does not exist.
        if (bill.getAppointment() != null) {
            accessPolicy.requireView(bill.getAppointment(), actor);
        }
        return bill;
    }

    public List<Bill> listFor(Session actor, java.time.LocalDate date) {
        return switch (actor.getRole()) {
            case ADMIN -> billDao.findByDateDetailed(date);
            case DENTIST -> List.of();   // dentists do not handle money
            case PATIENT -> patientDao.findByUserId(actor.getUserId())
                    .map(p -> billDao.findByPatientDetailed(p.getId()))
                    .orElseGet(List::of);
        };
    }

    /** Records payment against an issued bill. */
    public void recordPayment(String billNo, Session actor) {
        if (actor.hasRole(RoleCode.PATIENT)) {
            throw new BillingNotAllowedException("Only clinic staff can record a payment.");
        }
        Bill bill = findByNumber(billNo, actor);

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BillingNotAllowedException(billNo + " is already marked paid.");
        }
        if (bill.getStatus() == BillStatus.VOID) {
            throw new BillingNotAllowedException(billNo + " has been voided.");
        }

        transactions.inTransactionAs(actor.getUserId(), () -> billDao.markPaid(bill.getBillId()));
        LOG.info(() -> "Payment recorded for " + billNo + " by " + actor.getUsername());
    }

    public ClinicSettings settings() {
        return ClinicSettings.load(settingsDao);
    }

    public PricingStrategyFactory pricingFactory() {
        return pricingFactory;
    }
}
