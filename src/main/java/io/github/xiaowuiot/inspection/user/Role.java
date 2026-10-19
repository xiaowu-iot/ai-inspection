package io.github.xiaowuiot.inspection.user;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

    /** 内置角色编码 */
    public static final String ADMIN = "ADMIN";
    public static final String SAFETY_MANAGER = "SAFETY_MANAGER";
    public static final String USER = "USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    /** 内置角色不能删除，也不能改编码 */
    private boolean builtin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "permission", length = 40)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Role() {
    }

    public Role(String code, String name, String description, boolean builtin, Set<Permission> permissions) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.builtin = builtin;
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isBuiltin() { return builtin; }
    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
