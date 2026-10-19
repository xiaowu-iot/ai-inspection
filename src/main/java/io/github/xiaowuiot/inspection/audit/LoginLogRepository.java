package io.github.xiaowuiot.inspection.audit;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {

    @Modifying
    @Query("delete from LoginLog l where l.createdAt < ?1")
    int deleteBefore(LocalDateTime time);
}
