package lk.dentalclinic.service;

import lk.dentalclinic.dao.DentistDao;
import lk.dentalclinic.dao.PatientDao;
import lk.dentalclinic.model.Appointment;
import lk.dentalclinic.security.Session;

/**
 * Record-level access control — assumption A6.
 *
 * <p>{@link lk.dentalclinic.security.AccessRules} decides who may reach
 * {@code /appointments}; this decides which appointments they may see once there. No URL
 * pattern can express "your own", so both are needed and neither substitutes for the
 * other.
 *
 * <ul>
 *   <li><strong>ADMIN</strong> — everything.</li>
 *   <li><strong>DENTIST</strong> — appointments booked with them. A dentist has no
 *       business reading a colleague's list, and the clinical record is the patient's.</li>
 *   <li><strong>PATIENT</strong> — their own only.</li>
 * </ul>
 *
 * <p>A refusal here is reported as <em>not found</em>, never <em>forbidden</em>. See
 * {@link AppointmentNotFoundException} for why.
 */
public final class AppointmentAccessPolicy {

    private final PatientDao patientDao;
    private final DentistDao dentistDao;

    public AppointmentAccessPolicy(PatientDao patientDao, DentistDao dentistDao) {
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
    }

    public boolean canView(Appointment appointment, Session session) {
        return switch (session.getRole()) {
            case ADMIN -> true;
            case DENTIST -> dentistDao.findByUserId(session.getUserId())
                    .map(dentist -> dentist.getId() == appointment.getDentistId())
                    .orElse(false);
            case PATIENT -> patientDao.findByUserId(session.getUserId())
                    .map(patient -> patient.getId() == appointment.getPatientId())
                    .orElse(false);
        };
    }

    /**
     * Who may change the schedule — reschedule or cancel.
     *
     * <p>A dentist may not: moving a patient's appointment without asking them is a
     * clinic decision, not a clinical one, so it stays with the desk. A patient may,
     * within the 24-hour window {@link Appointment#canBeCancelledBy} enforces.
     */
    public boolean canReschedule(Appointment appointment, Session session) {
        return switch (session.getRole()) {
            case ADMIN -> true;
            case DENTIST -> false;
            case PATIENT -> canView(appointment, session)
                    && appointment.canBeCancelledBy(session.getRole());
        };
    }

    /** Who may mark a treatment complete: the dentist who performed it, or an administrator. */
    public boolean canComplete(Appointment appointment, Session session) {
        return switch (session.getRole()) {
            case ADMIN -> true;
            case DENTIST -> canView(appointment, session);
            case PATIENT -> false;
        };
    }

    /** Throws the not-found exception when the session may not see this appointment. */
    public void requireView(Appointment appointment, Session session) {
        if (!canView(appointment, session)) {
            throw new AppointmentNotFoundException(appointment.getAppointmentNo());
        }
    }
}
