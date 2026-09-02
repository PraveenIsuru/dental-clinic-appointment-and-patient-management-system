package lk.dentalclinic.web.handler.api;

import lk.dentalclinic.dao.DentistDao;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.dao.TreatmentDao;
import lk.dentalclinic.model.Patient;
import lk.dentalclinic.model.RoleCode;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.WebContext;
import lk.dentalclinic.web.dto.ApiDto;
import lk.dentalclinic.web.json.Json;

import java.util.List;

/**
 * Reference data over REST: dentists, treatments and patients.
 *
 * <pre>
 *   GET /api/v1/dentists            active dentists and their session hours
 *   GET /api/v1/treatments          the treatment catalogue with prices
 *   GET /api/v1/patients?q=         patient search  (staff only)
 * </pre>
 *
 * <p>These are what a client needs before it can post a booking — the dentist and
 * treatment identifiers, and the hours a booking must fall within. Without them the
 * booking endpoint would be unusable except by guessing ids, which is the difference
 * between an API and an endpoint.
 */
public final class CatalogApiHandler {

    private static final int SEARCH_LIMIT = 50;

    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final PatientDao patientDao;

    public CatalogApiHandler(DentistDao dentistDao, TreatmentDao treatmentDao,
                             PatientDao patientDao) {
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.patientDao = patientDao;
    }

    public Handler dentists() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            boolean includeInactive = ApiSupport.query(exchange, "all")
                    .map(Boolean::parseBoolean).orElse(false);

            var found = includeInactive ? dentistDao.findAll() : dentistDao.findActive();

            ApiSupport.okList(exchange, "dentists",
                    array -> array.addAll(found, ApiDto::dentistSummary), found.size());
        });
    }

    public Handler treatments() {
        return ApiSupport.guard(exchange -> {
            WebContext.requireSession();
            boolean includeInactive = ApiSupport.query(exchange, "all")
                    .map(Boolean::parseBoolean).orElse(false);

            var found = includeInactive ? treatmentDao.findAll() : treatmentDao.findActive();

            ApiSupport.okList(exchange, "treatments",
                    array -> array.addAll(found, ApiDto::treatment), found.size());
        });
    }

    /**
     * Patient search.
     *
     * <p>Staff only, and a patient asking gets their own record rather than a 403 — the
     * endpoint is still useful to them, and refusing outright would tell them nothing.
     * The result is capped, because an unbounded patient dump is a data-protection
     * incident waiting for a slow afternoon.
     */
    public Handler patients() {
        return ApiSupport.guard(exchange -> {
            Session actor = WebContext.requireSession();

            if (actor.hasRole(RoleCode.PATIENT)) {
                List<Patient> own = patientDao.findByUserId(actor.getUserId())
                        .map(List::of).orElseGet(List::of);
                ApiSupport.okList(exchange, "patients",
                        array -> array.addAll(own, ApiDto::patientDetail), own.size());
                return;
            }

            String search = ApiSupport.query(exchange, "q").orElse("").trim();
            List<Patient> found = search.isEmpty()
                    ? patientDao.findAll()
                    : patientDao.searchByNameOrContact(search);

            List<Patient> page = found.size() > SEARCH_LIMIT
                    ? found.subList(0, SEARCH_LIMIT) : found;

            Json.JsonObjectBuilder body = Json.object()
                    .put("count", page.size())
                    .put("truncated", found.size() > page.size());
            body.putArray("patients", array -> array.addAll(page, ApiDto::patientSummary));
            ApiSupport.ok(exchange, body);
        });
    }
}
