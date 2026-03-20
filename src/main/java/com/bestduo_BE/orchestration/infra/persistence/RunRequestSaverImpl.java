package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.RunRequestSaver;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.RunRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RunRequestSaverImpl implements RunRequestSaver {
  private final RunRequestJpaRepository repo;

  @Override
  @Transactional
  public RunRequest save(RunRequest request) {
    return repo.save(request);
  }
}
