package com.bestduo_BE.workitem.application;

import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkItemPoller {

  private final WorkItemJpaRepository workItemJpaRepository;
  private final WorkItemWorker workItemWorker;

  public WorkItemWorker.WorkerResult pollOnce() {
    WorkItem next = workItemJpaRepository.findAll().stream()
        .filter(item -> item.getStatus() == WorkItemStatus.READY)
        .min(Comparator.comparingInt(WorkItem::getPriority).thenComparing(WorkItem::getCreatedAt))
        .orElse(null);

    if (next == null) {
      return null;
    }

    return workItemWorker.execute(next.getId());
  }
}
