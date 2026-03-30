package com.bestduo_BE.workitem.infra.persistence.repository;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemJpaRepository extends JpaRepository<WorkItem, Long> {

  boolean existsByCoverageBucketIdAndTypeAndStatusIn(Long coverageBucketId, WorkItemType type, List<WorkItemStatus> statuses);

  long countByPatchAndTierAndStatus(String patch, Tier tier, WorkItemStatus status);
}
