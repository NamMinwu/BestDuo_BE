package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunRequest;

public interface SessionRunRequestSaver {
  SessionRunRequest save(SessionRunRequest request);
}
