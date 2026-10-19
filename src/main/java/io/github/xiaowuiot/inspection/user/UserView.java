package io.github.xiaowuiot.inspection.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返回给前端的用户信息，不含密码。
 */
public record UserView(Long id, String username, String displayName, String phone, String department,
                       boolean enabled, List<RoleRef> roles, List<String> permissions,
                       LocalDateTime createdAt, LocalDateTime lastLoginAt) {

    public record RoleRef(Long id, String code, String name) {
    }

    public static UserView of(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getPhone(), u.getDepartment(),
                u.isEnabled(),
                u.getRoles().stream().map(r -> new RoleRef(r.getId(), r.getCode(), r.getName())).toList(),
                u.permissions().stream().map(Enum::name).sorted().toList(),
                u.getCreatedAt(), u.getLastLoginAt());
    }

    /** 下拉选择用的精简信息 */
    public record Brief(Long id, String displayName, String department) {
        public static Brief of(User u) {
            return u == null ? null : new Brief(u.getId(), u.getDisplayName(), u.getDepartment());
        }
    }
}
