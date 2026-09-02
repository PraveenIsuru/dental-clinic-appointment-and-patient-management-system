package lk.dentalclinic.dao;

import lk.dentalclinic.model.Role;
import lk.dentalclinic.model.RoleCode;

import java.util.List;
import java.util.Optional;

public interface RoleDao {

    Optional<Role> findByCode(RoleCode code);

    Optional<Role> findById(int roleId);

    List<Role> findAll();
}
