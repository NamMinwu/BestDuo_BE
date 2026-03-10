package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.persistence.entity.SessionRunRequest;

public interface SessionRunRequestSaver {
  SessionRunRequest save(SessionRunRequest request);
}
