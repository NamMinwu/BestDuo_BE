package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.application.worker.WorkerContract;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkItemWorker {

  private final WorkItemDispatcher workItemDispatcher;
  private final Map<WorkItemType, WorkerContract> workers;

  public WorkItemWorker(
      WorkItemDispatcher workItemDispatcher,
      List<WorkerContract> workers
  ) {
    this.workItemDispatcher = workItemDispatcher;
    this.workers = new EnumMap<>(WorkItemType.class);
    workers.forEach(worker -> this.workers.put(worker.type(), worker));
  }

  public WorkerResult execute(WorkItem item) {
    WorkerContract worker = workers.get(item.getType());
    if (worker == null) {
      workItemDispatcher.markError(item.getId(), "No worker registered for type=" + item.getType());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    }
    try {
      worker.execute(item);
      workItemDispatcher.markDone(item.getId());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.DONE);
    } catch (BudgetExhaustedException | RiotRateLimitedException e) {
      workItemDispatcher.markPending(item.getId());
      throw e;
    } catch (Exception e) {
      workItemDispatcher.markError(item.getId(), e.getMessage());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    }
  }

  public record WorkerResult(Long workItemId, WorkItemType type, WorkItemStatus status) {
  }
}
