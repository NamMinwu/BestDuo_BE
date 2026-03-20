package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.RunLog;

public interface RunRequestStatusUpdater {
  void markRunning(Long requestId);

  void markDone(Long requestId, RunLog.RunResult result);

  void markError(Long requestId, String message);

}
