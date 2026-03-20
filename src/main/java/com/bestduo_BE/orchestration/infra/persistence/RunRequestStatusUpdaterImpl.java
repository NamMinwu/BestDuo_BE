package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.RunRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunLog;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.RunRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RunRequestStatusUpdaterImpl implements RunRequestStatusUpdater {
  private final RunRequestJpaRepository repository;

  @Override
  @Transactional
  public void markRunning(Long requestId) {
    RunRequest request = repository.findById(requestId).orElseThrow();
    request.markRunning();
  }

  @Override
  @Transactional
  public void markDone(Long requestId, RunLog.RunResult result) {
    RunRequest request = repository.findById(requestId).orElseThrow();
    request.markDone("DONE");
  }

  @Override
  @Transactional
  public void markError(Long requestId, String message) {
    RunRequest request = repository.findById(requestId).orElseThrow();
    request.markError(message);
  }

}
