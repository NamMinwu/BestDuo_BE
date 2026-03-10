package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.persistence.entity.SessionRunLog;

public interface SessionRunRequestStatusUpdater {
  void markRunning(Long requestId);

  void markDone(Long requestId, SessionRunLog.SessionResult result);

  void markError(Long requestId, String message);

}
