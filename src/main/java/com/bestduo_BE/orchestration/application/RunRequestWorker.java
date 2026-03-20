package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.RunRequestFinder;
import com.bestduo_BE.orchestration.application.port.RunRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunLog;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunRequestWorker {

  private final RunRequestFinder finder;
  private final RunRequestStatusUpdater statusUpdater;
  private final RunExecutor dailyRunExecutor;

  public void pollAndRunOnce() {
    if (finder.findRunning().isPresent()) {
      return;
    }

    var request = finder.findOldestRequested().orElse(null);
    if (request == null) {
      return;
    }

    Long runStartedAt = null;

    try {
      statusUpdater.markRunning(request.getId());

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

      RunLog.RunResult result = dailyRunExecutor.run(
          request.getBudgetTotal(),
          request.getSeedRatio(),
          request.getRefreshRatio(),
          request.getIngestLimitPerCycle(),
          request.getMaxIngestCycles(),
          request.getRefreshLimit(),
          request.getTier()
      );

      long elapsedMillis = Duration.ofNanos(System.nanoTime() - runStartedAt).toMillis();
      statusUpdater.markDone(request.getId(), result);

      log.info(
          "Run request completed. requestId={} stopReason={} elapsedMs={} seedEnqueued={} refreshEnqueued={} picked={} done={} error={} rawCreated={}",
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
      log.error("Run request failed. requestId={} elapsedMs={}", request.getId(), elapsedMillis, e);
      statusUpdater.markError(request.getId(), shorten(e.getMessage()));
    }
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= 500 ? s : s.substring(0, 500);
  }
}
