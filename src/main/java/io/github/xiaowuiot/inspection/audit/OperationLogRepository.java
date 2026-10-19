package io.github.xiaowuiot.inspection.audit;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    @Modifying
    @Query("delete from OperationLog o where o.createdAt < ?1")
    int deleteBefore(LocalDateTime time);
}
