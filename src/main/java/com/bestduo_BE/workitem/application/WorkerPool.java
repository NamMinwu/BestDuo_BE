package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "work-item", name = "worker-enabled", havingValue = "true")
public class WorkerPool {

  private final WorkItemProperties workItemProperties;
  private final WorkItemDispatcher workItemDispatcher;
  private final WorkItemWorker workItemWorker;
  private final RiotKeyPool riotKeyPool;

  private ExecutorService executorService;

  @PostConstruct
  void start() {
    // 서버 재시작 시 이전 RUNNING 아이템을 PENDING으로 복구
    workItemDispatcher.recoverStaleRunning(workItemProperties.getStaleMinutes());
    executorService = Executors.newVirtualThreadPerTaskExecutor();
    for (int i = 0; i < workItemProperties.getPoolSize(); i++) {
      executorService.submit(this::runLoop);
    }
  }

  void runLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        // key cooling 중이면 실제 남은 시간만큼 sleep → 공회전 방지
        Duration keyWait = riotKeyPool.durationUntilAnyAvailable();
        if (!keyWait.isZero()) {
          Thread.sleep(keyWait.toMillis() + 200);
          continue;
        }

        // TOCTOU 수정: lease 먼저 획득한 뒤 pickAndLock
        // lease 없이 pickAndLock 하면 다른 worker가 key를 선점한 경우 WorkItem이 RUNNING에 고착됨
        KeyLease lease;
        try {
          lease = riotKeyPool.leaseForWorker();
        } catch (RiotRateLimitedException e) {
          Thread.sleep(workItemProperties.getPollingIntervalMs());
          continue;
        }

        try (lease) {
          var picked = workItemDispatcher.pickAndLock(1);
          if (picked.isEmpty()) {
            Thread.sleep(workItemProperties.getPollingIntervalMs());
            continue;
          }
          workItemWorker.execute(picked.getFirst(), lease);
        } finally {
          riotKeyPool.clearWorkerLease();
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (BudgetExhaustedException | RiotRateLimitedException e) {
        // WorkItemWorker에서 markPending 완료. key 회복 대기 후 재시도.
        try {
          Thread.sleep(workItemProperties.getPollingIntervalMs());
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
        }
      } catch (Exception e) {
        log.error("[WorkerPool] runLoop unexpected error. thread={}", Thread.currentThread().getName(), e);
        try {
          Thread.sleep(workItemProperties.getPollingIntervalMs());
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @PreDestroy
  void stop() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }
}
