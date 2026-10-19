package io.github.xiaowuiot.inspection.inspection;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    /** 自己的记录，不含已删除的 */
    Page<Inspection> findByUserIdAndDeletedFalseOrderByIdDesc(Long userId, Pageable pageable);

    /**
     * 查全部记录。withDeleted：是否包含已删除的
     */
    @Query("select i from Inspection i where (:withDeleted = true or i.deleted = false) order by i.id desc")
    Page<Inspection> search(boolean withDeleted, Pageable pageable);

    @Query("select coalesce(sum(i.inputTokens), 0) from Inspection i where i.createdAt >= :from")
    long sumInputTokensSince(LocalDateTime from);

    @Query("select coalesce(sum(i.outputTokens), 0) from Inspection i where i.createdAt >= :from")
    long sumOutputTokensSince(LocalDateTime from);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
}
