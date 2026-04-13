package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestFinder;
import com.bestduo_BE.orchestration.application.port.ExecutionRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import com.bestduo_BE.common.domain.model.Tier;
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
  private final ExecutionOrchestrator executionOrchestrator;

  public void pollAndRunOnce() {
    ExecutionRequest request = findExecutableRequest();
    if (request == null) {
      return;
    }

    executeRequest(request);
  }

  private ExecutionRequest findExecutableRequest() {
    if (executionRequestFinder.findRunning().isPresent()) {
      return null;
    }

    return executionRequestFinder.findOldestRequested().orElse(null);
  }

  private void executeRequest(ExecutionRequest request) {
    Long runStartedAt = null;

    try {
      executionRequestStatusUpdater.markRunning(request.getId());

      runStartedAt = System.nanoTime();
      logStart(request);

      ExecutionLog.ExecutionResult result = runExecution(request);

      handleSuccess(request, result, runStartedAt);

    } catch (Exception e) {
      handleFailure(request, runStartedAt, e);
    }
  }

  private void logStart(ExecutionRequest request) {
    log.info(
        "Run started. requestId={} budgetTotal={} seedRatio={} ingestLimitPerCycle={} maxIngestCycles={} tier={}",
        request.getId(),
        request.getBudgetTotal(),
        request.getSeedRatio(),
        request.getIngestLimitPerCycle(),
        request.getMaxIngestCycles(),
        request.getTier()
    );
  }

  private ExecutionLog.ExecutionResult runExecution(ExecutionRequest request) {
    return executionOrchestrator.run(
        request.getBudgetTotal(),
        request.getSeedRatio(),
        request.getRefreshRatio(),
        request.getIngestLimitPerCycle(),
        request.getMaxIngestCycles(),
        request.getRefreshLimit(),
        request.getTier()
    );
  }

  private void handleSuccess(ExecutionRequest request, ExecutionLog.ExecutionResult result, Long runStartedAt) {
    long elapsedMillis = elapsedMillis(runStartedAt);
    Long requestId = request.getId();
    executionRequestStatusUpdater.markDone(requestId, result);

    log.info(
        "Execution request completed. requestId={} stopReason={} elapsedMs={} seedEnqueued={} picked={} done={} error={} rawCreated={}",
        requestId,
        result.stopReason(),
        elapsedMillis,
        result.seedEnqueued(),
        result.picked(),
        result.done(),
        result.error(),
        result.rawCreated()
    );
  }

  private void handleFailure(ExecutionRequest request, Long runStartedAt, Exception e) {
    Long requestId = request.getId();
    long elapsedMillis = elapsedMillis(runStartedAt);
    log.error("Execution request failed. requestId={} elapsedMs={}", requestId, elapsedMillis, e);
    executionRequestStatusUpdater.markError(requestId, shorten(e.getMessage()));
  }

  private long elapsedMillis(Long startedAtNanos) {
    if (startedAtNanos == null) {
      return 0;
    }

    return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
  }

  private String shorten(String s) {
    if (s == null) {
      return null;
    }

    return s.length() <= 500 ? s : s.substring(0, 500);
  }
}
