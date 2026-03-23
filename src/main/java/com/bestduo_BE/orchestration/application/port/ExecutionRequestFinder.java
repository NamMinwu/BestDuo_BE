package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import java.util.List;
import java.util.Optional;

public interface ExecutionRequestFinder {
  boolean existsActiveRequest();
  Optional<ExecutionRequest> findById(Long id);
  List<ExecutionRequest> findAllOrderByRequestedAtDesc();
  Optional<ExecutionRequest> findRunning();
  Optional<ExecutionRequest> findLatest();
  Optional<ExecutionRequest> findOldestRequested();
}
