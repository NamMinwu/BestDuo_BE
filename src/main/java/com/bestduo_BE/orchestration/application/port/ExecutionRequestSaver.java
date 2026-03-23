package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;

public interface ExecutionRequestSaver {
  ExecutionRequest save(ExecutionRequest request);
}
