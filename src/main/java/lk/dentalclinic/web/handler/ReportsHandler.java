package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.dao.ReportDao;
import lk.dentalclinic.event.AppointmentNotificationListener;
import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.web.Fragments;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * The four management reports — the grid's *"reports to facilitate decision-making"*.
 *
 * <p>Two are computed by the stored routines written in M1: revenue by treatment runs
 * {@code sp_daily_revenue_report}, and dentist workload reads {@code vw_dentist_workload}.
 * Saying so on the page itself is deliberate — it is the visible evidence that the
 * advanced database features are load-bearing rather than decorative.
 *
 * <p>Administrator-only, except the patient visit history, which a patient may run
 * against their own record.
 */
public final class ReportsHandler implements Handler {

    private final ReportDao reports;
    private final PatientDao patientDao;
    private final AppointmentNotificationListener notifications;
    private final View view;

    public ReportsHandler(ReportDao reports, PatientDao patientDao,
                          AppointmentNotificationListener notifications, View view) {
        this.reports = reports;
        this.patientDao = patientDao;
        this.notifications = notifications;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();
        LocalDate date = AppointmentHandler
                .parseDate(Requests.queryParam(exchange, "date").orElse(null))
                .orElse(LocalDate.now());

        Map<String, Object> model = view.model(exchange);
        model.put("dateIso", date.toString());
        model.put("date", date);

        // --- 1. daily operations -------------------------------------------
        ReportDao.DailyOperations operations = reports.dailyOperations(date);
        model.put("opsTotal", operations.total());
        model.put("opsCompleted", operations.completed());
        model.put("opsCancelled", operations.cancelled());
        model.put("opsUpcoming", operations.booked() + operations.confirmed());
        model.put("opsCompletionRate", operations.completionRate().toPlainString());
        model.put("opsMinutesBooked", operations.chairMinutesBooked());
        model.put("opsMinutesLost", operations.chairMinutesLost());
        model.put("opsHoursLost", String.format("%.1f", operations.chairMinutesLost() / 60.0));
        model.put("opsBilled", operations.billed().toPlainString());
        model.put("opsCollected", operations.collected().toPlainString());
        model.put("opsOutstanding", operations.outstanding().toPlainString());

        // --- 2. revenue by treatment (sp_daily_revenue_report) --------------
        model.put("revenueRows", Fragments.revenueTable(reports.revenueByTreatment(date)));

        // --- 3. dentist workload (vw_dentist_workload) ----------------------
        model.put("workloadRows", Fragments.workloadTable(reports.dentistWorkload()));

        // --- 4. patient visit history ---------------------------------------
        Integer patientId = AppointmentHandler
                .parseInt(Requests.queryParam(exchange, "patientId").orElse(null));
        if (patientId != null) {
            patientDao.findById(patientId).ifPresent(patient -> {
                model.put("historyPatient", patient.getFullName());
                model.put("historyPatientNo", patient.getPatientNo());
                model.put("historyRows",
                        Fragments.visitHistoryTable(reports.patientVisitHistory(patientId)));
                model.put("hasHistory", true);
            });
        }
        model.put("patientOptions", Fragments.patientOptions(patientDao.findAll(), patientId));

        // --- notification evidence (A12) ------------------------------------
        model.put("notificationCount", notifications.sentCount());
        model.put("notificationRows", Fragments.notificationList(notifications.recent()));

        view.render(exchange, "reports", model);
    }

    /**
     * {@code GET /patient/history} — a patient's own visit history.
     *
     * <p>The patient id comes from the session, never from the query string, so this
     * cannot be turned into a way to read somebody else's record.
     */
    public Handler myHistory() {
        return exchange -> {
            Session actor = WebContext.requireSession();
            Map<String, Object> model = view.model(exchange);

            patientDao.findByUserId(actor.getUserId()).ifPresent(patient -> {
                model.put("historyPatient", patient.getFullName());
                model.put("historyPatientNo", patient.getPatientNo());
                model.put("historyRows",
                        Fragments.visitHistoryTable(reports.patientVisitHistory(patient.getId())));
                model.put("hasHistory", true);
            });
            model.put("isOwnHistory", actor.hasRole(RoleCode.PATIENT));
            view.render(exchange, "visit-history", model);
        };
    }
}
