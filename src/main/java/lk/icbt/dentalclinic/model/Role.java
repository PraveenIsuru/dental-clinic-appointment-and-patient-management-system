package lk.icbt.dentalclinic.model;

import java.util.Objects;

/** A named role, loaded from the {@code roles} lookup table. */
public final class Role {

    private final int roleId;
    private final RoleCode code;
    private final String description;

    public Role(int roleId, RoleCode code, String description) {
        this.roleId = roleId;
        this.code = Objects.requireNonNull(code, "code");
        this.description = description;
    }

    public int getRoleId() {
        return roleId;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && role.code == this.code;
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code.name();
    }
}
