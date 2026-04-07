package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
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

  // WorkerPool 호출 경로: lease는 호출자가 관리 (TOCTOU 수정 후 사용)
  public WorkerResult execute(WorkItem item, KeyLease lease) {
    WorkerContract worker = workers.get(item.getType());
    if (worker == null) {
      workItemDispatcher.markError(item.getId(), "No worker registered for type=" + item.getType());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    }
    try {
      worker.execute(item, lease);
      workItemDispatcher.markDone(item.getId());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.DONE);
    } catch (BudgetExhaustedException | RiotRateLimitedException e) {
      // key 소진/rate limit — PENDING으로 복구해 다음 주기에 재시도
      workItemDispatcher.markPending(item.getId());
      throw e;
    } catch (Exception e) {
      workItemDispatcher.markError(item.getId(), e.getMessage());
      return new WorkerResult(item.getId(), item.getType(), WorkItemStatus.ERROR);
    }
    // lease 생명주기(close + clearWorkerLease)는 WorkerPool의 try-with-resources + finally가 담당
  }

  // WorkItemPoller / 테스트 호환 경로: lease를 내부에서 획득
  // markPending / markError / markDone은 execute(item, lease)에서 처리 → 여기서 중복 호출 불필요
  public WorkerResult execute(WorkItem item) {
    try (KeyLease lease = riotKeyPool.leaseForWorker()) {
      return execute(item, lease);
    } finally {
      riotKeyPool.clearWorkerLease();
    }
  }

  public record WorkerResult(Long workItemId, WorkItemType type, WorkItemStatus status) {
  }
}
