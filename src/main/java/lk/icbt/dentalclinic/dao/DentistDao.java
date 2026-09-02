package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Dentist;

import java.util.List;
import java.util.Optional;

public interface DentistDao {

    Optional<Dentist> findById(int dentistId);

    Optional<Dentist> findByUserId(int userId);

    List<Dentist> findAll();

    List<Dentist> findActive();

    /** @return the generated dentist id */
    int create(Dentist dentist);

    void update(Dentist dentist);

    /**
     * Retires a dentist without deleting them.
     *
     * <p>Deleting would orphan every appointment they ever performed — the foreign key
     * would refuse it, and forcing it through would destroy clinical history. Retiring
     * removes them from the booking drop-down while their past work stays intact.
     */
    void setActive(int dentistId, boolean active);
}
