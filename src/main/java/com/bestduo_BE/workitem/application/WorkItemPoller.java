package com.bestduo_BE.workitem.application;

import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkItemPoller {

  private final WorkItemDispatcher workItemDispatcher;
  private final WorkItemWorker workItemWorker;

  public WorkItemWorker.WorkerResult pollOnce() {
    WorkItem next = workItemDispatcher.pickAndLock(1).stream().findFirst().orElse(null);
    if (next == null) {
      return null;
    }
    return workItemWorker.execute(next);
  }
}
