package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Dentist;

import java.util.List;
import java.util.Optional;

public interface DentistDao {

    Optional<Dentist> findById(int dentistId);

    Optional<Dentist> findByUserId(int userId);

    List<Dentist> findAll();

    List<Dentist> findActive();
}
