package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.infra.riot.RiotRateLimitInterceptor;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
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
  private final RiotRateLimitInterceptor riotRateLimitInterceptor;

  private Thread workerThread;

  @PostConstruct
  void start() {
    // 서버 재시작 시 이전 RUNNING 아이템을 PENDING으로 복구
    workItemDispatcher.recoverStaleRunning(workItemProperties.getStaleMinutes());
    workerThread = Thread.ofVirtual().name("work-item-worker").start(this::runLoop);
  }

  void runLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        // 429 cooling 중이면 실제 남은 시간만큼 sleep → 공회전 방지
        Duration keyWait = riotRateLimitInterceptor.durationUntilAvailable();
        if (!keyWait.isZero()) {
          Thread.sleep(keyWait.toMillis() + 200);
          continue;
        }

        var picked = workItemDispatcher.pickAndLock(1);
        if (picked.isEmpty()) {
          Thread.sleep(workItemProperties.getPollingIntervalMs());
          continue;
        }
        workItemWorker.execute(picked.getFirst());

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
    if (workerThread != null) {
      workerThread.interrupt();
    }
  }
}
