package lk.icbt.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.icbt.dentalclinic.dao.DentistDao;
import lk.icbt.dentalclinic.dao.PatientDao;
import lk.icbt.dentalclinic.dao.TreatmentDao;
import lk.icbt.dentalclinic.model.Appointment;
import lk.icbt.dentalclinic.model.Patient;
import lk.icbt.dentalclinic.security.Session;
import lk.icbt.dentalclinic.service.AppointmentService;
import lk.icbt.dentalclinic.web.Fragments;
import lk.icbt.dentalclinic.web.Handler;
import lk.icbt.dentalclinic.web.Pages;
import lk.icbt.dentalclinic.web.Responses;
import lk.icbt.dentalclinic.web.View;
import lk.icbt.dentalclinic.web.WebContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The landing page for each role.
 *
 * <p>One handler serves all three, selecting the template from the session's role rather
 * than trusting anything in the request. A path such as {@code /admin/dashboard} is
 * already restricted by {@link lk.icbt.dentalclinic.security.AccessRules}, but choosing
 * the view from the session means that even a routing mistake cannot show a patient the
 * administrator's page.
 *
 * <p>Each dashboard now carries real appointment data, scoped by
 * {@link AppointmentService#listFor} — the administrator sees the whole day, a dentist
 * sees only their own, and a patient only their own upcoming visits.
 */
public final class DashboardHandler implements Handler {

    private static final int UPCOMING_LIMIT = 8;

    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final AppointmentService appointments;
    private final View view;

    public DashboardHandler(PatientDao patientDao, DentistDao dentistDao,
                            TreatmentDao treatmentDao, AppointmentService appointments,
                            View view) {
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.appointments = appointments;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session session = WebContext.requireSession();
        Map<String, Object> model = view.model(exchange);
        model.put("today", LocalDate.now());

        switch (session.getRole()) {
            case ADMIN -> renderAdmin(exchange, model, session);
            case DENTIST -> renderDentist(exchange, model, session);
            case PATIENT -> renderPatient(exchange, model, session);
        }
    }

    private void renderAdmin(HttpExchange exchange, Map<String, Object> model, Session session)
            throws IOException {
        List<Appointment> today = appointments.listFor(session, LocalDate.now());

        model.put("patientCount", patientDao.findAll().size());
        model.put("dentistCount", dentistDao.findActive().size());
        model.put("treatmentCount", treatmentDao.findActive().size());
        model.put("todayCount", today.size());
        model.put("todayRows", Fragments.appointmentTable(today, true));
        view.render(exchange, "dashboard-admin", model);
    }

    private void renderDentist(HttpExchange exchange, Map<String, Object> model, Session session)
            throws IOException {
        dentistDao.findByUserId(session.getUserId()).ifPresent(dentist -> {
            model.put("specialization", dentist.getSpecialization());
            model.put("sessionStart", Fragments.time(dentist.getSessionStart()));
            model.put("sessionEnd", Fragments.time(dentist.getSessionEnd()));
            model.put("hasProfile", true);
            model.put("dentistId", dentist.getId());
        });

        List<Appointment> today = appointments.listFor(session, LocalDate.now());
        model.put("todayCount", today.size());
        model.put("todayRows", Fragments.appointmentTable(today, true));
        view.render(exchange, "dashboard-dentist", model);
    }

    private void renderPatient(HttpExchange exchange, Map<String, Object> model, Session session)
            throws IOException {
        Optional<Patient> patient = patientDao.findByUserId(session.getUserId());
        if (patient.isEmpty()) {
            // A PATIENT login with no patient row should be impossible — registration
            // creates both in one transaction — so this means the data is inconsistent
            // and is worth surfacing rather than rendering a half-empty page.
            Responses.html(exchange, 500, Pages.serverError());
            return;
        }
        Patient record = patient.get();
        List<Appointment> upcoming = appointments.upcomingFor(session, UPCOMING_LIMIT);

        model.put("patientNo", record.getPatientNo());
        model.put("address", record.getAddress());
        model.put("contactNumber", record.getContactNumber());
        model.put("upcomingCount", upcoming.size());
        model.put("upcomingRows", Fragments.appointmentTable(upcoming, false));
        view.render(exchange, "dashboard-patient", model);
    }
}
