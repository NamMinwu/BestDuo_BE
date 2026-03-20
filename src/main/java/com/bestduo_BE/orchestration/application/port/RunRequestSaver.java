package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;

public interface RunRequestSaver {
  RunRequest save(RunRequest request);
}
