package com.bestduo_BE.workitem.application;

import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkItemPoller {

  private final WorkItemJpaRepository workItemJpaRepository;
  private final WorkItemWorker workItemWorker;

  public WorkItemWorker.WorkerResult pollOnce() {
    WorkItem next = workItemJpaRepository.findFirstByStatusOrderByPriorityAscCreatedAtAsc(WorkItemStatus.READY)
        .orElse(null);

    if (next == null) {
      return null;
    }

    return workItemWorker.execute(next.getId());
  }
}
