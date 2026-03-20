package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.RunRequestFinder;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.RunRequestJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunRequestFinderImpl implements RunRequestFinder {
  private final RunRequestJpaRepository repo;

  @Override
  public boolean existsActiveRequest() {
    return repo.existsByStatusIn(List.of("REQUESTED", "RUNNING"));
  }

  @Override
  public Optional<RunRequest> findById(Long id) {
    return repo.findById(id);
  }

  @Override
  public List<RunRequest> findAllOrderByRequestedAtDesc() {
    return repo.findAllByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<RunRequest> findRunning() {
    return repo.findFirstByStatusOrderByStartedAtDesc("RUNNING");
  }

  @Override
  public Optional<RunRequest> findLatest() {
    return repo.findTopByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<RunRequest> findOldestRequested() {
    return repo.findFirstByStatusOrderByStartedAtDesc("REQUESTED");
  }

}
