package lk.icbt.dentalclinic.dao.jdbc;

import lk.icbt.dentalclinic.dao.RoleDao;
import lk.icbt.dentalclinic.dao.RowMapper;
import lk.icbt.dentalclinic.model.Role;
import lk.icbt.dentalclinic.model.RoleCode;

import java.util.List;
import java.util.Optional;

public final class JdbcRoleDao extends AbstractJdbcDao implements RoleDao {

    private static final RowMapper<Role> MAPPER = rs -> new Role(
            rs.getInt("role_id"),
            RoleCode.of(rs.getString("code")),
            rs.getString("description"));

    public JdbcRoleDao(ConnectionPool pool) {
        super(pool);
    }

    @Override
    public Optional<Role> findByCode(RoleCode code) {
        return queryOne("SELECT role_id, code, description FROM roles WHERE code = ?",
                MAPPER, code);
    }

    @Override
    public Optional<Role> findById(int roleId) {
        return queryOne("SELECT role_id, code, description FROM roles WHERE role_id = ?",
                MAPPER, roleId);
    }

    @Override
    public List<Role> findAll() {
        return query("SELECT role_id, code, description FROM roles ORDER BY role_id", MAPPER);
    }
}
