package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;

public interface ExecutionRequestStatusUpdater {
  void markRunning(Long requestId);

  void markDone(Long requestId, ExecutionLog.ExecutionResult result);

  void markError(Long requestId, String message);

}
