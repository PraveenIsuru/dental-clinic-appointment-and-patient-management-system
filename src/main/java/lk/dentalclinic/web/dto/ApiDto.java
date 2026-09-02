package lk.icbt.dentalclinic.web.dto;

import lk.icbt.dentalclinic.dao.ReportDao;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.Bill;
import lk.icbt.dentalclinic.model.Dentist;
import lk.icbt.dentalclinic.model.Patient;
import lk.icbt.dentalclinic.model.Treatment;
import lk.icbt.dentalclinic.web.json.Json;

/**
 * DTO — the mapping from domain objects to the JSON the API publishes.
 *
 * <p><strong>Why this class exists at all.</strong> Serialising entities directly is the
 * obvious shortcut and it is how private data escapes. Three concrete leaks this layer
 * prevents:
 *
 * <ul>
 *   <li>{@code User} would carry its password hash. (It has no getter for it, so this is
 *       belt and braces — but the belt matters.)</li>
 *   <li>{@code Patient} would carry a date of birth and full address to any caller who
 *       asked for an appointment list, when only the treating clinic needs them.</li>
 *   <li>Renaming a field in the domain model would silently change the public API and
 *       break every client, with nothing in the build to warn about it.</li>
 * </ul>
 *
 * <p>Writing the shape out by hand also means the API is <em>designed</em> rather than
 * being whatever the entities happen to contain. That is worth more than the few lines it
 * costs.
 *
 * <p>Two levels are offered for most types: a summary for list responses and a full form
 * for a single-resource response. A list of forty appointments should not carry forty
 * complete patient records.
 */
public final class ApiDto {

    private ApiDto() {
    }

    // ------------------------------------------------------------- appointments

    /** Summary form, for list responses. */
    public static void appointmentSummary(Json.JsonObjectBuilder json, Appointment appointment) {
        json.put("appointmentNo", appointment.getAppointmentNo())
                .put("date", appointment.getAppointmentDate())
                .put("time", appointment.getAppointmentTime())
                .put("status", appointment.getStatus());

        if (appointment.getPatient() != null) {
            json.put("patientName", appointment.getPatient().getFullName())
                    .put("patientNo", appointment.getPatient().getPatientNo());
        }
        if (appointment.getDentist() != null) {
            json.put("dentistName", appointment.getDentist().getFullName());
        }
        if (appointment.getTreatment() != null) {
            json.put("treatment", appointment.getTreatment().getName());
        }
    }

    /** Full form, for a single appointment. */
    public static void appointmentDetail(Json.JsonObjectBuilder json, Appointment appointment) {
        json.put("appointmentNo", appointment.getAppointmentNo())
                .put("date", appointment.getAppointmentDate())
                .put("time", appointment.getAppointmentTime())
                .put("status", appointment.getStatus())
                .put("billable", appointment.isBillable())
                .putIfPresent("notes", appointment.getNotes());

        if (appointment.getPatient() != null) {
            json.putObject("patient", p -> patientDetail(p, appointment.getPatient()));
        }
        if (appointment.getDentist() != null) {
            json.putObject("dentist", d -> dentistSummary(d, appointment.getDentist()));
        }
        if (appointment.getTreatment() != null) {
            json.putObject("treatment", t -> treatment(t, appointment.getTreatment()));
        }
    }

    // ---------------------------------------------------------------- patients

    /**
     * Deliberately omits the date of birth.
     *
     * <p>It is on the record because a clinician may need it, but it is not needed to
     * display or manage an appointment, and an API that hands out dates of birth to every
     * caller is a data-protection problem waiting to happen. The brief's ETHICAL criterion
     * asks for exactly this kind of judgement.
     */
    public static void patientDetail(Json.JsonObjectBuilder json, Patient patient) {
        json.put("patientNo", patient.getPatientNo())
                .put("name", patient.getFullName())
                .put("contactNumber", patient.getContactNumber())
                .putIfPresent("address", patient.getAddress())
                .putIfPresent("email", patient.getEmail())
                .put("hasLogin", patient.hasLogin());
    }

    public static void patientSummary(Json.JsonObjectBuilder json, Patient patient) {
        json.put("patientNo", patient.getPatientNo())
                .put("name", patient.getFullName())
                .put("contactNumber", patient.getContactNumber());
    }

    // ---------------------------------------------------------------- dentists

    public static void dentistSummary(Json.JsonObjectBuilder json, Dentist dentist) {
        json.put("id", dentist.getId())
                .put("name", dentist.getFullName())
                .put("specialization", dentist.getSpecialization())
                .put("sessionStart", dentist.getSessionStart())
                .put("sessionEnd", dentist.getSessionEnd())
                .put("active", dentist.isActive());
    }

    // -------------------------------------------------------------- treatments

    public static void treatment(Json.JsonObjectBuilder json, Treatment treatment) {
        json.put("id", treatment.getTreatmentId())
                .put("code", treatment.getCode())
                .put("name", treatment.getName())
                .put("family", treatment.getFamily())
                .putIfPresent("description", treatment.getDescription())
                .put("baseCost", treatment.getBaseCost())
                .put("durationMinutes", treatment.getDurationMinutes())
                .put("active", treatment.isActive());
    }

    // ------------------------------------------------------------------- bills

    public static void billSummary(Json.JsonObjectBuilder json, Bill bill) {
        json.put("billNo", bill.getBillNo())
                .put("total", bill.getTotalAmount())
                .put("status", bill.getStatus())
                .put("issuedAt", bill.getIssuedAt());

        if (bill.getAppointment() != null) {
            json.put("appointmentNo", bill.getAppointment().getAppointmentNo());
        }
    }

    public static void billDetail(Json.JsonObjectBuilder json, Bill bill) {
        json.put("billNo", bill.getBillNo())
                .put("status", bill.getStatus())
                .put("issuedAt", bill.getIssuedAt())
                .put("paidAt", bill.getPaidAt())
                .putObject("charges", c -> c
                        .put("consultationFee", bill.getConsultationFee())
                        .put("treatmentCharge", bill.getTreatmentCharge())
                        .put("subtotal", bill.subtotal())
                        .put("discount", bill.getDiscountAmount())
                        .put("tax", bill.getTaxAmount())
                        .put("total", bill.getTotalAmount()))
                .putArray("lineItems", items -> items.addAll(bill.getLineItems(),
                        (item, line) -> item
                                .put("description", line.description())
                                .put("quantity", line.quantity())
                                .put("unitPrice", line.unitPrice())
                                .put("lineTotal", line.lineTotal())));

        if (bill.getAppointment() != null) {
            json.putObject("appointment", a -> appointmentSummary(a, bill.getAppointment()));
        }
    }

    // ----------------------------------------------------------------- reports

    public static void dailyOperations(Json.JsonObjectBuilder json,
                                       ReportDao.DailyOperations operations) {
        json.put("date", operations.date())
                .putObject("appointments", a -> a
                        .put("total", operations.total())
                        .put("booked", operations.booked())
                        .put("confirmed", operations.confirmed())
                        .put("completed", operations.completed())
                        .put("cancelled", operations.cancelled())
                        .put("completionRatePct", operations.completionRate()))
                .putObject("chairTime", c -> c
                        .put("minutesBooked", operations.chairMinutesBooked())
                        .put("minutesLost", operations.chairMinutesLost()))
                .putObject("revenue", r -> r
                        .put("billed", operations.billed())
                        .put("collected", operations.collected())
                        .put("outstanding", operations.outstanding()));
    }

    public static void treatmentRevenue(Json.JsonObjectBuilder json,
                                        ReportDao.TreatmentRevenue row) {
        json.put("treatment", row.treatment())
                .put("isTotal", row.isTotal())
                .put("billsIssued", row.billsIssued())
                .put("consultationFees", row.consultationFees())
                .put("treatmentCharges", row.treatmentCharges())
                .put("discounts", row.discounts())
                .put("totalBilled", row.totalBilled())
                .put("totalCollected", row.totalCollected());
    }

    public static void dentistWorkload(Json.JsonObjectBuilder json,
                                       ReportDao.DentistWorkload row) {
        json.put("dentistId", row.dentistId())
                .put("name", row.fullName())
                .put("specialization", row.specialization())
                .put("totalAppointments", row.totalAppointments())
                .put("completed", row.completed())
                .put("cancelled", row.cancelled())
                .put("upcoming", row.upcoming())
                .put("completionRatePct", row.completionRatePct());
    }
}
