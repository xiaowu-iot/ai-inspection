package io.github.xiaowuiot.inspection.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select count(u) from User u join u.roles r where r.id = :roleId")
    long countUsers(Long roleId);
}
