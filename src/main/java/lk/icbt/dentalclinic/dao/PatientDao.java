package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Patient;

import java.util.List;
import java.util.Optional;

public interface PatientDao {

    Optional<Patient> findById(int patientId);

    Optional<Patient> findByPatientNo(String patientNo);

    /** The patient record belonging to a self-service login, if there is one (A5). */
    Optional<Patient> findByUserId(int userId);

    List<Patient> findAll();

    List<Patient> searchByNameOrContact(String term);

    /** @return the generated patient id */
    int create(Patient patient);

    void update(Patient patient);

    /**
     * Allocates the next {@code PAT-000000} number.
     *
     * <p>Must run inside a transaction: it reads the current maximum and returns the
     * successor, so without the surrounding transaction two concurrent registrations
     * could receive the same number.
     */
    String nextPatientNo();
}
