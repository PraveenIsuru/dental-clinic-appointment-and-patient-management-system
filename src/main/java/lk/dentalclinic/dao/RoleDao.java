package lk.icbt.dentalclinic.dao;

import lk.icbt.dentalclinic.model.Role;
import lk.icbt.dentalclinic.model.RoleCode;

import java.util.List;
import java.util.Optional;

public interface RoleDao {

    Optional<Role> findByCode(RoleCode code);

    Optional<Role> findById(int roleId);

    List<Role> findAll();
}
