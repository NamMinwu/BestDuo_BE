package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.ExecutionRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExecutionRequestStatusUpdaterImpl implements ExecutionRequestStatusUpdater {
  private final ExecutionRequestJpaRepository repository;

  @Override
  @Transactional
  public void markRunning(Long requestId) {
    ExecutionRequest request = repository.findById(requestId).orElseThrow();
    request.markRunning();
  }

  @Override
  @Transactional
  public void markDone(Long requestId, ExecutionLog.ExecutionResult result) {
    ExecutionRequest request = repository.findById(requestId).orElseThrow();
    request.markDone("DONE");
  }

  @Override
  @Transactional
  public void markError(Long requestId, String message) {
    ExecutionRequest request = repository.findById(requestId).orElseThrow();
    request.markError(message);
  }

}
