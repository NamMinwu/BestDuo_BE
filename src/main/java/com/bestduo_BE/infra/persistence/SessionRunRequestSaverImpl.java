package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.SessionRunRequestSaver;
import com.bestduo_BE.infra.persistence.entity.SessionRunRequest;
import com.bestduo_BE.infra.persistence.repository.SessionRunRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SessionRunRequestSaverImpl implements SessionRunRequestSaver {
  private final SessionRunRequestJpaRepository repo;

  @Override
  @Transactional
  public SessionRunRequest save(SessionRunRequest request) {
    return repo.save(request);
  }
}
