package lk.icbt.dentalclinic.web.handler.api;

import lk.icbt.dentalclinic.dao.ReportDao;
import lk.icbt.dentalclinic.model.Bill;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.BillingService;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Router;
import lk.icbt.dentalclinic.web.WebContext;
import lk.icbt.dentalclinic.web.dto.ApiDto;
import lk.icbt.dentalclinic.web.json.Json;
import lk.icbt.dentalclinic.web.json.JsonObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Billing and reporting over REST.
 *
 * <pre>
 *   GET  /api/v1/bills?date=YYYY-MM-DD    bills the caller may see
 *   GET  /api/v1/bills/{no}               one bill, itemised
 *   POST /api/v1/bills                    issue a bill for a completed appointment
 *   POST /api/v1/bills/{no}/pay           record payment
 *   GET  /api/v1/reports/daily?date=      daily operations
 *   GET  /api/v1/reports/revenue?date=    sp_daily_revenue_report
 *   GET  /api/v1/reports/workload         vw_dentist_workload
 * </pre>
 *
 * <p>The report endpoints matter for the brief beyond convenience: they are the clearest
 * demonstration that the stored procedure and the view are load-bearing. A client calling
 * {@code /api/v1/reports/revenue} receives the output of {@code sp_daily_revenue_report}
 * with no Java aggregation between the database and the JSON.
 */
public final class BillApiHandler {

    private final BillingService billing;
    private final ReportDao reports;

    public BillApiHandler(BillingService billing, ReportDao reports) {
        this.billing = billing;
        this.reports = reports;
    }

    // ------------------------------------------------------------------- bills

    public Handler list() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            LocalDate date = ApiSupport.query(exchange, "date")
                    .map(LocalDate::parse).orElse(LocalDate.now());

            List<Bill> found = billing.listFor(actor, date);

            ApiSupport.okList(exchange, "bills",
                    array -> array.addAll(found, ApiDto::billSummary), found.size());
        });
    }

    public Handler get() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            String billNo = Router.pathParam(exchange, "no");

            Bill bill = billing.findByNumber(billNo, actor);

            Json.JsonObjectBuilder body = Json.object();
            ApiDto.billDetail(body, bill);
            ApiSupport.ok(exchange, body);
        });
    }

    public Handler create() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            JsonObject body = ApiSupport.readBody(exchange);

            String appointmentNo = body.requireString("appointmentNo");
            int quantity = body.getInt("quantity").orElse(1);
            BigDecimal discount = body.getNumber("discountPercent").orElse(BigDecimal.ZERO);

            Bill bill = billing.generate(appointmentNo, quantity, discount, actor);

            Json.JsonObjectBuilder response = Json.object();
            ApiDto.billDetail(response, bill);
            ApiSupport.created(exchange, "/api/v1/bills/" + bill.getBillNo(), response);
        });
    }

    public Handler pay() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();
            String billNo = Router.pathParam(exchange, "no");

            billing.recordPayment(billNo, actor);

            Json.JsonObjectBuilder body = Json.object();
            ApiDto.billDetail(body, billing.findByNumber(billNo, actor));
            ApiSupport.ok(exchange, body);
        });
    }

    // ----------------------------------------------------------------- reports

    public Handler dailyReport() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            LocalDate date = ApiSupport.query(exchange, "date")
                    .map(LocalDate::parse).orElse(LocalDate.now());

            Json.JsonObjectBuilder body = Json.object();
            ApiDto.dailyOperations(body, reports.dailyOperations(date));
            ApiSupport.ok(exchange, body);
        });
    }

    public Handler revenueReport() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            LocalDate date = ApiSupport.query(exchange, "date")
                    .map(LocalDate::parse).orElse(LocalDate.now());

            var rows = reports.revenueByTreatment(date);

            Json.JsonObjectBuilder body = Json.object()
                    .put("date", date)
                    .put("source", "sp_daily_revenue_report")
                    .put("count", rows.size());
            body.putArray("rows", array -> array.addAll(rows, ApiDto::treatmentRevenue));
            ApiSupport.ok(exchange, body);
        });
    }

    public Handler workloadReport() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            var rows = reports.dentistWorkload();

            Json.JsonObjectBuilder body = Json.object()
                    .put("source", "vw_dentist_workload")
                    .put("count", rows.size());
            body.putArray("dentists", array -> array.addAll(rows, ApiDto::dentistWorkload));
            ApiSupport.ok(exchange, body);
        });
    }
}
