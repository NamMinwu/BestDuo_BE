package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.SessionRunRequestStatusUpdater;
import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunLog;
import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.SessionRunRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SessionRunRequestStatusUpdaterImpl implements SessionRunRequestStatusUpdater {
  private final SessionRunRequestJpaRepository repository;

  @Override
  @Transactional
  public void markRunning(Long requestId) {
    SessionRunRequest request = repository.findById(requestId).orElseThrow();
    request.markRunning();
  }

  @Override
  @Transactional
  public void markDone(Long requestId, SessionRunLog.SessionResult result) {
    SessionRunRequest request = repository.findById(requestId).orElseThrow();
    request.markDone("DONE");
  }

  @Override
  @Transactional
  public void markError(Long requestId, String message) {
    SessionRunRequest request = repository.findById(requestId).orElseThrow();
    request.markError(message);
  }

}
