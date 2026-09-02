package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.model.Bill;
import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.service.AppointmentNotFoundException;
import lk.dentalclinic.service.AppointmentService;
import lk.dentalclinic.service.BillNotFoundException;
import lk.dentalclinic.service.BillingNotAllowedException;
import lk.dentalclinic.service.BillingService;
import lk.dentalclinic.service.ValidationException;
import lk.dentalclinic.web.Fragments;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Pages;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.Responses;
import lk.dentalclinic.web.Router;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * Calculating and issuing bills — brief requirement 4.
 *
 * <p>{@code GET /bills} lists what the session may see, {@code GET /bills/new} prices an
 * appointment before committing, {@code POST /bills} issues it, {@code GET /bills/{no}}
 * shows it, and {@code POST /bills/{no}/pay} records payment.
 *
 * <p>The quotation step matters at a counter: the clerk can tell the patient the figure,
 * apply a discount and see the effect, all before anything is written. Only the final
 * POST creates a record, and a bill once issued is never edited.
 */
public final class BillingHandler implements Handler {

    private final BillingService billing;
    private final AppointmentService appointments;
    private final View view;

    public BillingHandler(BillingService billing, AppointmentService appointments, View view) {
        this.billing = billing;
        this.appointments = appointments;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            issue(exchange);
        } else {
            list(exchange);
        }
    }

    // ------------------------------------------------------------------ list

    private void list(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        LocalDate date = AppointmentHandler
                .parseDate(Requests.queryParam(exchange, "date").orElse(null))
                .orElse(LocalDate.now());

        var bills = billing.listFor(actor, date);

        Map<String, Object> model = view.model(exchange);
        model.put("dateIso", date.toString());
        model.put("date", date);
        model.put("count", bills.size());
        model.put("rows", Fragments.billTable(bills, !actor.hasRole(RoleCode.PATIENT)));
        model.put("isPatient", actor.hasRole(RoleCode.PATIENT));
        model.put("isStaff", !actor.hasRole(RoleCode.PATIENT));
        Requests.queryParam(exchange, "paid")
                .ifPresent(no -> model.put("alert", "Payment recorded for " + no + "."));
        view.render(exchange, "bills", model);
    }

    // ------------------------------------------------------------- quotation

    /** {@code GET /bills/new?appointmentNo=…} — prices without saving. */
    public Handler quoteForm() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            if (actor.hasRole(RoleCode.PATIENT)) {
                Responses.html(exchange, 403, Pages.forbidden(actor.dashboardPath()));
                return;
            }

            String appointmentNo = Requests.queryParam(exchange, "appointmentNo").orElse("");
            Map<String, Object> model = view.model(exchange);
            model.put("appointmentNo", appointmentNo);
            model.put("quantity", Requests.queryParam(exchange, "quantity").orElse("1"));
            model.put("discountPercent", Requests.queryParam(exchange, "discountPercent")
                    .orElse("0"));

            if (appointmentNo.isBlank()) {
                view.render(exchange, "bill-new", model);
                return;
            }

            try {
                Appointment appointment = appointments.findByNumber(appointmentNo, actor);
                populateQuote(model, appointment, actor,
                        AppointmentHandler.parseInt(model.get("quantity").toString()),
                        decimal(model.get("discountPercent").toString()));
                view.render(exchange, "bill-new", model);
            } catch (AppointmentNotFoundException e) {
                model.put("error", "No appointment found for " + appointmentNo + ".");
                view.render(exchange, 404, "bill-new", model);
            } catch (BillingNotAllowedException e) {
                model.put("error", e.getMessage());
                view.render(exchange, 409, "bill-new", model);
            }
        };
    }

    private void populateQuote(Map<String, Object> model, Appointment appointment,
                               Session actor, Integer quantity, BigDecimal discount) {
        if (!appointment.isBillable()) {
            model.put("error", "Only a completed appointment can be billed. "
                    + appointment.getAppointmentNo() + " is " + appointment.getStatus() + ".");
            return;
        }

        BillingService.Quotation quote = billing.quote(appointment,
                quantity == null ? 1 : quantity,
                discount == null ? BigDecimal.ZERO : discount);

        model.put("hasQuote", true);
        model.put("patientName", appointment.getPatient() == null
                ? "" : appointment.getPatient().getFullName());
        model.put("treatmentName", appointment.getTreatment() == null
                ? "" : appointment.getTreatment().getName());
        model.put("pricingRule", quote.pricingRule());
        model.put("consultationFee", quote.consultationFee().toPlainString());
        model.put("treatmentCharge", quote.treatmentCharge().toPlainString());
        model.put("discountAmount", quote.discountAmount().toPlainString());
        model.put("taxAmount", quote.taxAmount().toPlainString());
        model.put("total", quote.total().toPlainString());
        model.put("maxDiscount", billing.settings().maxDiscountPercent()
                .stripTrailingZeros().toPlainString());
    }

    // ------------------------------------------------------------------ issue

    private void issue(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        Map<String, String> form = Requests.form(exchange);
        String appointmentNo = Requests.field(form, "appointmentNo");

        Integer quantity = AppointmentHandler.parseInt(form.get("quantity"));
        BigDecimal discount = decimal(Requests.field(form, "discountPercent"));

        try {
            Bill bill = billing.generate(appointmentNo,
                    quantity == null ? 1 : quantity,
                    discount == null ? BigDecimal.ZERO : discount,
                    actor);
            Responses.redirect(exchange, "/bills/" + enc(bill.getBillNo()) + "?issued=1");

        } catch (AppointmentNotFoundException e) {
            Responses.html(exchange, 404, Pages.notFound());
        } catch (BillingNotAllowedException e) {
            redisplayQuote(exchange, actor, form, 409, e.getMessage());
        } catch (ValidationException e) {
            redisplayQuote(exchange, actor, form, 422,
                    e.result().firstError().orElse("That bill is not valid."));
        }
    }

    private void redisplayQuote(HttpExchange exchange, Session actor, Map<String, String> form,
                                int status, String error) throws IOException {
        Map<String, Object> model = view.model(exchange);
        String appointmentNo = Requests.field(form, "appointmentNo");
        model.put("appointmentNo", appointmentNo);
        model.put("quantity", Requests.field(form, "quantity"));
        model.put("discountPercent", Requests.field(form, "discountPercent"));
        model.put("error", error);

        try {
            populateQuote(model, appointments.findByNumber(appointmentNo, actor), actor,
                    AppointmentHandler.parseInt(form.get("quantity")),
                    decimal(Requests.field(form, "discountPercent")));
        } catch (RuntimeException ignored) {
            // The quote could not be rebuilt; the error message alone is enough.
        }
        view.render(exchange, status, "bill-new", model);
    }

    // ------------------------------------------------------------------ detail

    /** {@code GET /bills/{no}} and {@code POST /bills/{no}/pay}. */
    public Handler detail() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            String billNo = Router.pathParam(exchange, "no");

            try {
                Bill bill = billing.findByNumber(billNo, actor);
                Map<String, Object> model = view.model(exchange);
                putBill(model, bill, actor);
                Requests.queryParam(exchange, "issued")
                        .ifPresent(flag -> model.put("justIssued", true));
                view.render(exchange, "bill-detail", model);
            } catch (BillNotFoundException | AppointmentNotFoundException e) {
                Responses.html(exchange, 404, Pages.notFound());
            }
        };
    }

    /** {@code POST /bills/{no}/pay}. */
    public Handler pay() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            String billNo = Router.pathParam(exchange, "no");
            try {
                billing.recordPayment(billNo, actor);
                Responses.redirect(exchange, "/bills/" + enc(billNo) + "?paidNow=1");
            } catch (BillNotFoundException | AppointmentNotFoundException e) {
                Responses.html(exchange, 404, Pages.notFound());
            } catch (BillingNotAllowedException e) {
                Bill bill = billing.findByNumber(billNo, actor);
                Map<String, Object> model = view.model(exchange);
                putBill(model, bill, actor);
                model.put("error", e.getMessage());
                view.render(exchange, 409, "bill-detail", model);
            }
        };
    }

    /**
     * {@code GET /bills/{no}/receipt} — the printable receipt of requirement 4.
     *
     * <p>Rendered from a standalone template with no navigation, so the printed page
     * carries only what belongs on paper.
     */
    public Handler receipt() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            String billNo = Router.pathParam(exchange, "no");
            try {
                Bill bill = billing.findByNumber(billNo, actor);
                Map<String, Object> model = view.model(exchange);
                putBill(model, bill, actor);
                model.put("clinicName", billing.settings().clinicName());
                model.put("clinicAddress", billing.settings().clinicAddress());
                view.render(exchange, "receipt", model);
            } catch (BillNotFoundException | AppointmentNotFoundException e) {
                Responses.html(exchange, 404, Pages.notFound());
            }
        };
    }

    private void putBill(Map<String, Object> model, Bill bill, Session actor) {
        Appointment appointment = bill.getAppointment();

        model.put("billNo", bill.getBillNo());
        model.put("statusBadge", Fragments.billStatusBadge(bill.getStatus()));
        model.put("status", bill.getStatus().name());
        model.put("consultationFee", bill.getConsultationFee().toPlainString());
        model.put("treatmentCharge", bill.getTreatmentCharge().toPlainString());
        model.put("subtotal", bill.subtotal().toPlainString());
        model.put("discountAmount", bill.getDiscountAmount().toPlainString());
        model.put("hasDiscount", bill.getDiscountAmount().signum() > 0);
        model.put("taxAmount", bill.getTaxAmount().toPlainString());
        model.put("hasTax", bill.getTaxAmount().signum() > 0);
        model.put("total", bill.getTotalAmount().toPlainString());
        model.put("issuedAt", bill.getIssuedAt());
        model.put("paidAt", bill.getPaidAt());
        model.put("isPaid", bill.isPaid());
        model.put("lineItems", Fragments.lineItemRows(bill.getLineItems()));

        if (appointment != null) {
            model.put("appointmentNo", appointment.getAppointmentNo());
            model.put("appointmentDate", appointment.getAppointmentDate());
            if (appointment.getPatient() != null) {
                model.put("patientName", appointment.getPatient().getFullName());
                model.put("patientNo", appointment.getPatient().getPatientNo());
                model.put("address", appointment.getPatient().getAddress());
                model.put("contactNumber", appointment.getPatient().getContactNumber());
            }
            if (appointment.getDentist() != null) {
                model.put("dentistName", appointment.getDentist().getFullName());
            }
            if (appointment.getTreatment() != null) {
                model.put("treatmentName", appointment.getTreatment().getName());
            }
        }
        model.put("canRecordPayment",
                !actor.hasRole(RoleCode.PATIENT) && !bill.isPaid()
                        && bill.getStatus() != lk.dentalclinic.model.BillStatus.VOID);
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            // A malformed figure is a validation failure, not a crash; zero lets the
            // quote render and the service report the real problem.
            return BigDecimal.ZERO;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
