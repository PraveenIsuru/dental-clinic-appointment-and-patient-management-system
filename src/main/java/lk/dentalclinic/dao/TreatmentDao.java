package lk.dentalclinic.dao;

import lk.dentalclinic.model.Treatment;

import java.util.List;
import java.util.Optional;

public interface TreatmentDao {

    Optional<Treatment> findById(int treatmentId);

    Optional<Treatment> findByCode(String code);

    List<Treatment> findAll();

    List<Treatment> findActive();

    boolean existsByCode(String code);

    /** @return the generated treatment id */
    int create(Treatment treatment);

    void update(Treatment treatment);

    /** Retires a treatment. Past appointments keep referring to it; see DentistDao. */
    void setActive(int treatmentId, boolean active);
}
