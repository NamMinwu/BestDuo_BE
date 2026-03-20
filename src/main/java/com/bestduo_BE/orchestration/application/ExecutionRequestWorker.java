package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestFinder;
import com.bestduo_BE.orchestration.application.port.ExecutionRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionRequestWorker {

  private final ExecutionRequestFinder executionRequestFinder;
  private final ExecutionRequestStatusUpdater executionRequestStatusUpdater;
  private final RunExecutor runExecutor;

  public void pollAndRunOnce() {
    if (executionRequestFinder.findRunning().isPresent()) {
      return;
    }

    var request = executionRequestFinder.findOldestRequested().orElse(null);
    if (request == null) {
      return;
    }

    Long runStartedAt = null;

    try {
      executionRequestStatusUpdater.markRunning(request.getId());

      runStartedAt = System.nanoTime();
      log.info(
          "Run started. requestId={} budgetTotal={} seedRatio={} refreshRatio={} ingestLimitPerCycle={} maxIngestCycles={} refreshLimit={} tier={}",
          request.getId(),
          request.getBudgetTotal(),
          request.getSeedRatio(),
          request.getRefreshRatio(),
          request.getIngestLimitPerCycle(),
          request.getMaxIngestCycles(),
          request.getRefreshLimit(),
          request.getTier());

      ExecutionLog.ExecutionResult result = runExecutor.run(
          request.getBudgetTotal(),
          request.getSeedRatio(),
          request.getRefreshRatio(),
          request.getIngestLimitPerCycle(),
          request.getMaxIngestCycles(),
          request.getRefreshLimit(),
          request.getTier()
      );

      long elapsedMillis = Duration.ofNanos(System.nanoTime() - runStartedAt).toMillis();
      executionRequestStatusUpdater.markDone(request.getId(), result);

      log.info(
          "Execution request completed. requestId={} stopReason={} elapsedMs={} seedEnqueued={} refreshEnqueued={} picked={} done={} error={} rawCreated={}",
          request.getId(),
          result.stopReason(),
          elapsedMillis,
          result.seedEnqueued(),
          result.refreshEnqueued(),
          result.picked(),
          result.done(),
          result.error(),
          result.rawCreated()
      );

    } catch (Exception e) {
      long elapsedMillis = runStartedAt == null ? 0 : Duration.ofNanos(System.nanoTime() - runStartedAt).toMillis();
      log.error("Execution request failed. requestId={} elapsedMs={}", request.getId(), elapsedMillis, e);
      executionRequestStatusUpdater.markError(request.getId(), shorten(e.getMessage()));
    }
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= 500 ? s : s.substring(0, 500);
  }
}
