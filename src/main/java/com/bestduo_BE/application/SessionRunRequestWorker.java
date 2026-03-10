package com.bestduo_BE.application;

import com.bestduo_BE.application.port.SessionRunRequestFinder;
import com.bestduo_BE.application.port.SessionRunRequestStatusUpdater;
import com.bestduo_BE.infra.persistence.entity.SessionRunLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRunRequestWorker {

  private final SessionRunRequestFinder finder;
  private final SessionRunRequestStatusUpdater statusUpdater;
  private final SessionRunner sessionRunner;

  public void pollAndRunOnce() {
    if (finder.findRunning().isPresent()) {
      return;
    }

    var request = finder.findOldestRequested().orElse(null);
    if (request == null) {
      return;
    }

    try {
      statusUpdater.markRunning(request.getId());

      SessionRunLog.SessionResult result = sessionRunner.run(
          request.getBudgetTotal(),
          request.getSeedRatio(),
          request.getRefreshRatio(),
          request.getConsumeLimitPerCycle(),
          request.getMaxConsumeCycles()
      );

      statusUpdater.markDone(request.getId(), result);

      log.info(
          "Session run request completed. requestId={}, stopReason={}",
          request.getId(),
          result.stopReason()
      );

    } catch (Exception e) {
      log.error("Session run request failed. requestId={}", request.getId(), e);
      statusUpdater.markError(request.getId(), shorten(e.getMessage()));
    }
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= 500 ? s : s.substring(0, 500);
  }
}
