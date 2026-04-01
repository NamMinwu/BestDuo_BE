package com.bestduo_BE.workitem.infra.persistence.repository;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface WorkItemJpaRepository extends JpaRepository<WorkItem, Long> {

  boolean existsByCoverageBucketIdAndTypeAndStatusIn(Long coverageBucketId, WorkItemType type, List<WorkItemStatus> statuses);

  long countByPatchAndTierAndStatus(String patch, Tier tier, WorkItemStatus status);

  long countByTypeAndPatchAndTierAndStatus(WorkItemType type, String patch, Tier tier, WorkItemStatus status);

  long countByTypeAndPatchAndTierAndStatusIn(WorkItemType type, String patch, Tier tier, List<WorkItemStatus> statuses);

  @Query(value = """
      select *
      from work_item wi
      where wi.status = 'PENDING'
      order by wi.priority asc, wi.created_at asc
      limit :limit
      for update skip locked
      """, nativeQuery = true)
  List<WorkItem> pickPendingForUpdate(@Param("limit") int limit);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      update work_item
      set status = 'PENDING',
          locked_at = null,
          updated_at = now()
      where status = 'RUNNING'
        and locked_at is not null
        and locked_at <= now() - (:staleMinutes || ' minutes')::interval
      """, nativeQuery = true)
  int recoverStaleRunning(@Param("staleMinutes") int staleMinutes);

  Optional<WorkItem> findFirstByStatusOrderByPriorityAscCreatedAtAsc(WorkItemStatus status);
}
