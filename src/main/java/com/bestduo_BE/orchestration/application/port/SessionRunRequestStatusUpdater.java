package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunLog;

public interface SessionRunRequestStatusUpdater {
  void markRunning(Long requestId);

  void markDone(Long requestId, SessionRunLog.SessionResult result);

  void markError(Long requestId, String message);

}
