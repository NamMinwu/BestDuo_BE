package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.SessionRunRequestFinder;
import com.bestduo_BE.infra.persistence.entity.SessionRunRequest;
import com.bestduo_BE.infra.persistence.repository.SessionRunRequestJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionRunRequestFinderImpl implements SessionRunRequestFinder {
  private final SessionRunRequestJpaRepository repo;

  @Override
  public boolean existsActiveRequest() {
    return repo.existsByStatusIn(List.of("REQUESTED", "RUNNING"));
  }

  @Override
  public Optional<SessionRunRequest> findById(Long id) {
    return repo.findById(id);
  }

  @Override
  public List<SessionRunRequest> findAllOrderByRequestedAtDesc() {
    return repo.findAllByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<SessionRunRequest> findRunning() {
    return repo.findFirstByStatusOrderByStartedAtDesc("RUNNING");
  }

  @Override
  public Optional<SessionRunRequest> findLatest() {
    return repo.findTopByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<SessionRunRequest> findOldestRequested() {
    return repo.findFirstByStatusOrderByStartedAtDesc("REQUESTED");
  }

}
