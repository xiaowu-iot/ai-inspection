package io.github.xiaowuiot.inspection.hazard;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HazardRepository extends JpaRepository<Hazard, Long> {

    List<Hazard> findByInspectionIdOrderById(Long inspectionId);

    /**
     * 查隐患。"待上报""已忽略"属于上报人的私有草稿：别人查不到，只有 viewerId 本人能查到自己的。
     */
    @Query("select h from Hazard h where (:status is null or h.status = :status) "
            + "and (:reporterId is null or h.reporter.id = :reporterId) "
            + "and (:level is null or h.level = :level) "
            + "and (h.status not in :privateStatuses or h.reporter.id = :viewerId) order by h.id desc")
    Page<Hazard> search(HazardStatus status, Long reporterId, String level, Long viewerId,
                        Collection<HazardStatus> privateStatuses, Pageable pageable);

    long countByStatus(HazardStatus status);
}
