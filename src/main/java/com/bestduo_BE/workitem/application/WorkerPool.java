package com.bestduo_BE.workitem.application;

import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "work-item", name = "worker-enabled", havingValue = "true")
public class WorkerPool {

  private final WorkItemProperties workItemProperties;
  private final WorkItemDispatcher workItemDispatcher;
  private final WorkItemWorker workItemWorker;

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

  private void runLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        var picked = workItemDispatcher.pickAndLock(1);
        if (picked.isEmpty()) {
          Thread.sleep(workItemProperties.getPollingIntervalMs());
          continue;
        }
        workItemWorker.execute(picked.getFirst());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        try {
          Thread.sleep(Duration.ofMillis(workItemProperties.getPollingIntervalMs()).toMillis());
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
