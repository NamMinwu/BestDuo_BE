package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.application.worker.WorkerContract;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class WorkItemWorker {

  private final WorkItemDispatcher workItemDispatcher;
  private final RiotKeyPool riotKeyPool;
  private final Map<WorkItemType, WorkerContract> workers;

  public WorkItemWorker(
      WorkItemDispatcher workItemDispatcher,
      RiotKeyPool riotKeyPool,
      List<WorkerContract> workers
  ) {
    this.workItemDispatcher = workItemDispatcher;
    this.riotKeyPool = riotKeyPool;
    this.workers = new EnumMap<>(WorkItemType.class);
    workers.forEach(worker -> this.workers.put(worker.type(), worker));
  }

  public WorkerResult execute(WorkItem item) {
    WorkerContract worker = workers.get(item.getType());
    if (worker == null) {
      workItemDispatcher.markError(item.getId(), "No worker registered for type=" + item.getType());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    }

    try (KeyLease ignored = riotKeyPool.leaseForWorker()) {
      worker.execute(item, ignored);
      workItemDispatcher.markDone(item.getId());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.DONE);
    } catch (Exception e) {
      workItemDispatcher.markError(item.getId(), e.getMessage());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    } finally {
      riotKeyPool.clearWorkerLease();
    }
  }

  public record WorkerResult(Long workItemId, WorkItemType type, WorkItemStatus status) {
  }
}
