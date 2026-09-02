package lk.dentalclinic.web;

import lk.dentalclinic.dao.ReportDao;
import lk.dentalclinic.event.AppointmentNotificationListener;
import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.model.AppointmentStatus;
import lk.dentalclinic.model.Bill;
import lk.dentalclinic.model.BillLineItem;
import lk.dentalclinic.model.BillStatus;
import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.Treatment;
import lk.dentalclinic.service.AppointmentService.SlotState;
import lk.dentalclinic.service.AppointmentService.SlotView;
import lk.dentalclinic.util.Html;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Builds the list and table markup the templates cannot.
 *
 * <p>{@link TemplateEngine} has no iteration construct — a deliberate omission, since a
 * general loop needs nested scopes and a path expression syntax, which is several
 * hundred lines reimplementing a solved problem badly. The cost is paid here: list
 * markup is assembled in Java and injected through the raw {@code {{{...}}} } form.
 *
 * <p>Everything interpolated goes through {@link Html#escape}. That is the whole reason
 * this is one class rather than string concatenation scattered across handlers — a
 * single place to be sure of it.
 */
public final class Fragments {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Fragments() {
    }

    public static String time(LocalTime time) {
        return time == null ? "" : TIME.format(time);
    }

    // -------------------------------------------------------------- appointments

    /** The appointment table shared by the day view, a dentist's list and a patient's history. */
    public static String appointmentTable(List<Appointment> appointments, boolean showPatient) {
        if (appointments.isEmpty()) {
            return "<p class=\"muted\">No appointments to show.</p>";
        }

        StringBuilder html = new StringBuilder("""
                <div class="table-wrap">
                <table class="data">
                  <thead><tr>
                    <th>Number</th><th>Date</th><th>Time</th>
                """);
        if (showPatient) {
            html.append("    <th>Patient</th><th>Contact</th>\n");
        }
        html.append("""
                    <th>Dentist</th><th>Treatment</th><th>Status</th><th></th>
                  </tr></thead>
                  <tbody>
                """);

        for (Appointment a : appointments) {
            html.append("    <tr>")
                    .append(cell(a.getAppointmentNo(), "mono"))
                    .append(cell(DATE.format(a.getAppointmentDate()), null))
                    .append(cell(TIME.format(a.getAppointmentTime()), null));
            if (showPatient) {
                Patient patient = a.getPatient();
                html.append(cell(patient == null ? "" : patient.getFullName(), null))
                        .append(cell(patient == null ? "" : patient.getContactNumber(), null));
            }
            html.append(cell(a.getDentist() == null ? "" : a.getDentist().getFullName(), null))
                    .append(cell(a.getTreatment() == null ? "" : a.getTreatment().getName(), null))
                    .append("<td>").append(statusBadge(a.getStatus())).append("</td>")
                    .append("<td><a href=\"/appointments/")
                    .append(Html.escape(a.getAppointmentNo()))
                    .append("\">View</a></td>")
                    .append("</tr>\n");
        }

        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String statusBadge(AppointmentStatus status) {
        String tone = switch (status) {
            case BOOKED -> "neutral";
            case CONFIRMED -> "good";
            case COMPLETED -> "done";
            case CANCELLED -> "bad";
        };
        return "<span class=\"badge " + tone + "\">" + Html.escape(status.name()) + "</span>";
    }

    // ------------------------------------------------------------------ pickers

    public static String dentistOptions(List<Dentist> dentists, Integer selectedId) {
        StringBuilder html = new StringBuilder("<option value=\"\">Choose a dentist…</option>\n");
        for (Dentist d : dentists) {
            html.append("<option value=\"").append(d.getId()).append('"')
                    .append(selected(selectedId, d.getId())).append('>')
                    .append(Html.escape(d.getFullName()))
                    .append(" — ").append(Html.escape(d.getSpecialization()))
                    .append(" (").append(time(d.getSessionStart()))
                    .append('–').append(time(d.getSessionEnd())).append(')')
                    .append("</option>\n");
        }
        return html.toString();
    }

    public static String treatmentOptions(List<Treatment> treatments, Integer selectedId) {
        StringBuilder html = new StringBuilder("<option value=\"\">Choose a treatment…</option>\n");
        for (Treatment t : treatments) {
            html.append("<option value=\"").append(t.getTreatmentId()).append('"')
                    .append(selected(selectedId, t.getTreatmentId())).append('>')
                    .append(Html.escape(t.getName()))
                    .append(" — LKR ").append(t.getBaseCost().toPlainString())
                    .append(" (").append(t.getDurationMinutes()).append(" min)")
                    .append("</option>\n");
        }
        return html.toString();
    }

    public static String timeOptions(List<LocalTime> slots, LocalTime selected) {
        if (slots.isEmpty()) {
            return "<option value=\"\">No free times — choose another date</option>";
        }
        StringBuilder html = new StringBuilder("<option value=\"\">Choose a time…</option>\n");
        for (LocalTime slot : slots) {
            html.append("<option value=\"").append(time(slot)).append('"')
                    .append(slot.equals(selected) ? " selected" : "").append('>')
                    .append(time(slot)).append("</option>\n");
        }
        return html.toString();
    }

    /** The suggestions offered when a slot is taken. */
    public static String slotSuggestions(List<LocalTime> suggestions) {
        if (suggestions.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder(" The next free times are ");
        for (int i = 0; i < suggestions.size(); i++) {
            if (i > 0) {
                html.append(i == suggestions.size() - 1 ? " and " : ", ");
            }
            html.append("<strong>").append(time(suggestions.get(i))).append("</strong>");
        }
        return html.append('.').toString();
    }

    // ------------------------------------------------------------ availability

    /** The free/busy grid for one dentist on one day. */
    public static String availabilityGrid(List<SlotView> slots, int dentistId, String date) {
        if (slots.isEmpty()) {
            return "<p class=\"muted\">Choose a dentist and a date to see their availability.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"slots\">\n");
        for (SlotView slot : slots) {
            String css = switch (slot.state()) {
                case FREE -> "slot free";
                case BOOKED -> "slot booked";
                case OFF_DUTY -> "slot off";
                case PAST -> "slot past";
            };
            String label = time(slot.time());
            if (slot.state() == SlotState.FREE) {
                html.append("  <a class=\"").append(css).append("\" href=\"/appointments/new?dentistId=")
                        .append(dentistId).append("&date=").append(Html.escape(date))
                        .append("&time=").append(label).append("\">")
                        .append(label).append("<span>free</span></a>\n");
            } else {
                String note = switch (slot.state()) {
                    case BOOKED -> slot.appointment() != null && slot.appointment().getPatient() != null
                            ? Html.escape(slot.appointment().getPatient().getFullName())
                            : "booked";
                    case OFF_DUTY -> "off duty";
                    case PAST -> "past";
                    case FREE -> "";
                };
                html.append("  <div class=\"").append(css).append("\">")
                        .append(label).append("<span>").append(note).append("</span></div>\n");
            }
        }
        return html.append("</div>\n").toString();
    }

    // ------------------------------------------------------------ record tables

    public static String patientTable(List<Patient> patients) {
        if (patients.isEmpty()) {
            return "<p class=\"muted\">No patients on file.</p>";
        }
        StringBuilder html = new StringBuilder("""
                <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Number</th><th>Name</th><th>Contact</th>
                  <th>Address</th><th>Login</th></tr></thead>
                  <tbody>
                """);
        for (Patient p : patients) {
            html.append("    <tr>")
                    .append(cell(p.getPatientNo(), "mono"))
                    .append(cell(p.getFullName(), null))
                    .append(cell(p.getContactNumber(), null))
                    .append(cell(p.getAddress(), null))
                    .append("<td>").append(p.hasLogin()
                            ? "<span class=\"badge good\">yes</span>"
                            : "<span class=\"badge neutral\">desk</span>").append("</td>")
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String dentistTable(List<Dentist> dentists) {
        if (dentists.isEmpty()) {
            return "<p class=\"muted\">No dentists on file.</p>";
        }
        StringBuilder html = new StringBuilder("""
                <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Name</th><th>Specialisation</th><th>Session</th>
                  <th>Phone</th><th>Status</th></tr></thead>
                  <tbody>
                """);
        for (Dentist d : dentists) {
            html.append("    <tr>")
                    .append(cell(d.getFullName(), null))
                    .append(cell(d.getSpecialization(), null))
                    .append(cell(time(d.getSessionStart()) + "–" + time(d.getSessionEnd()), null))
                    .append(cell(d.getContactNumber(), null))
                    .append("<td>").append(d.isActive()
                            ? "<span class=\"badge good\">active</span>"
                            : "<span class=\"badge bad\">inactive</span>").append("</td>")
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String treatmentTable(List<Treatment> treatments) {
        if (treatments.isEmpty()) {
            return "<p class=\"muted\">No treatments on file.</p>";
        }
        StringBuilder html = new StringBuilder("""
                <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Code</th><th>Treatment</th><th>Family</th>
                  <th class="num">Cost (LKR)</th><th class="num">Minutes</th>
                  <th>Status</th></tr></thead>
                  <tbody>
                """);
        for (Treatment t : treatments) {
            html.append("    <tr>")
                    .append(cell(t.getCode(), "mono"))
                    .append(cell(t.getName(), null))
                    .append(cell(t.getFamily().name(), null))
                    .append(cell(t.getBaseCost().toPlainString(), "num"))
                    .append(cell(String.valueOf(t.getDurationMinutes()), "num"))
                    .append("<td>").append(t.isActive()
                            ? "<span class=\"badge good\">offered</span>"
                            : "<span class=\"badge neutral\">retired</span>").append("</td>")
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }


    // ------------------------------------------------------------------- bills

    public static String billTable(List<Bill> bills, boolean showPatient) {
        if (bills.isEmpty()) {
            return "<p class=\"muted\">No bills to show.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\">\n<table class=\"data\">\n"
                + "  <thead><tr><th>Bill</th><th>Appointment</th>\n");
        if (showPatient) {
            html.append("    <th>Patient</th>\n");
        }
        html.append("    <th>Treatment</th><th class=\"num\">Total (LKR)</th>\n")
                .append("    <th>Status</th><th></th></tr></thead>\n  <tbody>\n");

        for (Bill bill : bills) {
            Appointment appointment = bill.getAppointment();
            html.append("    <tr>")
                    .append(cell(bill.getBillNo(), "mono"))
                    .append(cell(appointment == null ? "" : appointment.getAppointmentNo(), "mono"));
            if (showPatient) {
                html.append(cell(appointment == null || appointment.getPatient() == null
                        ? "" : appointment.getPatient().getFullName(), null));
            }
            html.append(cell(appointment == null || appointment.getTreatment() == null
                            ? "" : appointment.getTreatment().getName(), null))
                    .append(cell(bill.getTotalAmount().toPlainString(), "num"))
                    .append("<td>").append(billStatusBadge(bill.getStatus())).append("</td>")
                    .append("<td><a href=\"/bills/").append(Html.escape(bill.getBillNo()))
                    .append("\">View</a></td>")
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String billStatusBadge(BillStatus status) {
        String tone = switch (status) {
            case ISSUED -> "neutral";
            case PAID -> "good";
            case VOID -> "bad";
        };
        return "<span class=\"badge " + tone + "\">" + Html.escape(status.name()) + "</span>";
    }

    /** The itemised rows on a bill and on the printed receipt. */
    public static String lineItemRows(List<BillLineItem> items) {
        if (items.isEmpty()) {
            return "<tr><td colspan=\"3\" class=\"muted\">No items.</td></tr>";
        }
        StringBuilder html = new StringBuilder();
        for (BillLineItem item : items) {
            html.append("<tr>")
                    .append(cell(item.description(), null))
                    .append(cell(String.valueOf(item.quantity()), "num"))
                    .append(cell(item.lineTotal().toPlainString(), "num"))
                    .append("</tr>\n");
        }
        return html.toString();
    }

    // ----------------------------------------------------------------- reports

    /** Rows from sp_daily_revenue_report; the ROLLUP row is styled as a footer. */
    public static String revenueTable(List<ReportDao.TreatmentRevenue> rows) {
        if (rows.isEmpty()) {
            return "<p class=\"muted\">No bills were issued on this date.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\">\n<table class=\"data\">\n"
                + "  <thead><tr><th>Treatment</th><th class=\"num\">Bills</th>"
                + "<th class=\"num\">Consultation</th><th class=\"num\">Treatment</th>"
                + "<th class=\"num\">Discounts</th><th class=\"num\">Billed</th>"
                + "<th class=\"num\">Collected</th></tr></thead>\n  <tbody>\n");

        for (ReportDao.TreatmentRevenue row : rows) {
            html.append(row.isTotal() ? "    <tr class=\"total-row\">" : "    <tr>")
                    .append(cell(row.treatment(), null))
                    .append(cell(String.valueOf(row.billsIssued()), "num"))
                    .append(cell(row.consultationFees().toPlainString(), "num"))
                    .append(cell(row.treatmentCharges().toPlainString(), "num"))
                    .append(cell(row.discounts().toPlainString(), "num"))
                    .append(cell(row.totalBilled().toPlainString(), "num"))
                    .append(cell(row.totalCollected().toPlainString(), "num"))
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    /** Rows from vw_dentist_workload. */
    public static String workloadTable(List<ReportDao.DentistWorkload> rows) {
        if (rows.isEmpty()) {
            return "<p class=\"muted\">No dentists on file.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\">\n<table class=\"data\">\n"
                + "  <thead><tr><th>Dentist</th><th>Specialisation</th>"
                + "<th class=\"num\">Total</th><th class=\"num\">Completed</th>"
                + "<th class=\"num\">Cancelled</th><th class=\"num\">Upcoming</th>"
                + "<th class=\"num\">Completion</th></tr></thead>\n  <tbody>\n");

        for (ReportDao.DentistWorkload row : rows) {
            html.append("    <tr>")
                    .append(cell(row.fullName(), null))
                    .append(cell(row.specialization(), null))
                    .append(cell(String.valueOf(row.totalAppointments()), "num"))
                    .append(cell(String.valueOf(row.completed()), "num"))
                    .append(cell(String.valueOf(row.cancelled()), "num"))
                    .append(cell(String.valueOf(row.upcoming()), "num"))
                    .append(cell(row.completionRatePct().toPlainString() + "%", "num"))
                    .append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String visitHistoryTable(List<ReportDao.PatientVisit> visits) {
        if (visits.isEmpty()) {
            return "<p class=\"muted\">No visits recorded.</p>";
        }
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\">\n<table class=\"data\">\n"
                + "  <thead><tr><th>Appointment</th><th>Date</th><th>Dentist</th>"
                + "<th>Treatment</th><th>Status</th><th>Bill</th>"
                + "<th class=\"num\">Total (LKR)</th></tr></thead>\n  <tbody>\n");

        for (ReportDao.PatientVisit visit : visits) {
            html.append("    <tr>")
                    .append("<td class=\"mono\"><a href=\"/appointments/")
                    .append(Html.escape(visit.appointmentNo())).append("\">")
                    .append(Html.escape(visit.appointmentNo())).append("</a></td>")
                    .append(cell(DATE.format(visit.date()), null))
                    .append(cell(visit.dentist(), null))
                    .append(cell(visit.treatment(), null))
                    .append("<td>")
                    .append(statusBadge(AppointmentStatus.of(visit.status())))
                    .append("</td>");
            if (visit.hasBill()) {
                html.append("<td class=\"mono\"><a href=\"/bills/")
                        .append(Html.escape(visit.billNo())).append("\">")
                        .append(Html.escape(visit.billNo())).append("</a></td>")
                        .append(cell(visit.total().toPlainString(), "num"));
            } else {
                html.append("<td class=\"muted\">not billed</td>").append(cell("", "num"));
            }
            html.append("</tr>\n");
        }
        return html.append("  </tbody>\n</table>\n</div>\n").toString();
    }

    public static String patientOptions(List<Patient> patients, Integer selectedId) {
        StringBuilder html = new StringBuilder("<option value=\"\">Choose a patient…</option>\n");
        for (Patient p : patients) {
            html.append("<option value=\"").append(p.getId()).append('"')
                    .append(selected(selectedId, p.getId())).append('>')
                    .append(Html.escape(p.getFullName()))
                    .append(" (").append(Html.escape(p.getPatientNo())).append(')')
                    .append("</option>\n");
        }
        return html.toString();
    }

    /**
     * The confirmations the Observer listener produced — the visible evidence for A12
     * that the asynchronous notification really ran after the booking committed.
     */
    public static String notificationList(
            List<AppointmentNotificationListener.Notification> notifications) {
        if (notifications.isEmpty()) {
            return "<p class=\"muted\">No confirmations sent since the server started.</p>";
        }
        StringBuilder html = new StringBuilder("<ul class=\"notifications\">\n");
        for (AppointmentNotificationListener.Notification n : notifications) {
            html.append("  <li><span class=\"badge neutral\">")
                    .append(Html.escape(n.channel())).append("</span> ")
                    .append(Html.escape(n.body())).append("</li>\n");
        }
        return html.append("</ul>\n").toString();
    }

    // ------------------------------------------------------------------ helpers

    private static String cell(String value, String cssClass) {
        return "<td" + (cssClass == null ? "" : " class=\"" + cssClass + "\"") + ">"
                + Html.escape(value) + "</td>";
    }

    private static String selected(Integer selectedId, int candidate) {
        return selectedId != null && selectedId == candidate ? " selected" : "";
    }

    /** Copies form values back into the model so a rejected form redisplays what was typed. */
    public static void echo(Map<String, Object> model, Map<String, String> form, String... fields) {
        for (String field : fields) {
            model.put(field, form.getOrDefault(field, ""));
        }
    }
}
