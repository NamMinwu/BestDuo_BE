package com.bestduo_BE.workitem.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;

public interface WorkItemDispatcher {

  WorkItem emit(WorkItem workItem);

  List<WorkItem> pickAndLock(int limit);

  int recoverStaleRunning(int staleMinutes);

  void markDone(Long workItemId);

  void markError(Long workItemId, String message);

  long countByTypePatchTierStatus(WorkItemType type, String patch, Tier tier, WorkItemStatus status);

  long countByTypePatchTierStatuses(WorkItemType type, String patch, Tier tier, List<WorkItemStatus> statuses);
}
