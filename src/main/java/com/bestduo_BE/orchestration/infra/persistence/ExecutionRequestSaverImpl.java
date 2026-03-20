package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestSaver;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.ExecutionRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExecutionRequestSaverImpl implements ExecutionRequestSaver {
  private final ExecutionRequestJpaRepository repo;

  @Override
  @Transactional
  public ExecutionRequest save(ExecutionRequest request) {
    return repo.save(request);
  }
}
