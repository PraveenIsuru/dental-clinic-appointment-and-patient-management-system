package lk.dentalclinic.web.handler;

import com.sun.net.httpserver.HttpExchange;
import lk.dentalclinic.dao.DentistDao;
import lk.dentalclinic.model.Dentist;
import lk.dentalclinic.security.Session;
import lk.dentalclinic.service.AppointmentService;
import lk.dentalclinic.web.Fragments;
import lk.dentalclinic.web.Handler;
import lk.dentalclinic.web.Requests;
import lk.dentalclinic.web.View;
import lk.dentalclinic.web.WebContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dentist availability grid — the <em>Check Dentist Availability</em> use case, made
 * directly visible.
 *
 * <p>It is included by <em>Book Appointment</em> in the use case diagram, and the service
 * runs it on every booking. Exposing it as a page as well means the receptionist can
 * answer "when is Dr Silva free on Thursday?" without starting a booking and reading the
 * refusal, which is how the clinic's paper diary was actually used.
 *
 * <p>A free cell links straight into the booking form with the dentist, date and time
 * already filled in.
 *
 * <p>A dentist viewing this sees their own day by default; the drop-down is theirs alone,
 * because a dentist has no business reading a colleague's schedule.
 */
public final class AvailabilityHandler implements Handler {

    private final AppointmentService appointments;
    private final DentistDao dentistDao;
    private final View view;

    public AvailabilityHandler(AppointmentService appointments, DentistDao dentistDao,
                               View view) {
        this.appointments = appointments;
        this.dentistDao = dentistDao;
        this.view = view;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session actor = WebContext.requireSession();

        List<Dentist> selectable = switch (actor.getRole()) {
            case DENTIST -> dentistDao.findByUserId(actor.getUserId())
                    .map(List::of).orElseGet(List::of);
            case ADMIN, PATIENT -> dentistDao.findActive();
        };

        LocalDate date = AppointmentHandler
                .parseDate(Requests.queryParam(exchange, "date").orElse(null))
                .orElse(LocalDate.now());

        Integer dentistId = AppointmentHandler
                .parseInt(Requests.queryParam(exchange, "dentistId").orElse(null));
        if (dentistId == null && !selectable.isEmpty()) {
            dentistId = selectable.get(0).getId();
        }

        // Never trust the parameter: a dentist may only ask about themselves.
        final Integer requested = dentistId;
        boolean permitted = selectable.stream().anyMatch(d -> d.getId() == requested);

        Map<String, Object> model = view.model(exchange);
        model.put("dateIso", date.toString());
        model.put("minDate", LocalDate.now().minusMonths(1).toString());
        model.put("dentistOptions", Fragments.dentistOptions(selectable, dentistId));
        model.put("canChooseDentist", selectable.size() > 1);

        if (dentistId != null && permitted) {
            model.put("grid", Fragments.availabilityGrid(
                    appointments.daySlots(dentistId, date), dentistId, date.toString()));
            model.put("freeCount", appointments.freeSlots(dentistId, date).size());
            Optional<Dentist> dentist = selectable.stream()
                    .filter(d -> d.getId() == requested).findFirst();
            dentist.ifPresent(d -> {
                model.put("dentistName", d.getFullName());
                model.put("sessionStart", Fragments.time(d.getSessionStart()));
                model.put("sessionEnd", Fragments.time(d.getSessionEnd()));
            });
            model.put("hasGrid", true);
        }

        view.render(exchange, "availability", model);
    }
}
