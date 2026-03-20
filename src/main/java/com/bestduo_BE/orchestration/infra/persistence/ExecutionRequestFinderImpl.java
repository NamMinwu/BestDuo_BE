package com.bestduo_BE.orchestration.infra.persistence;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestFinder;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import com.bestduo_BE.orchestration.infra.persistence.repository.ExecutionRequestJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionRequestFinderImpl implements ExecutionRequestFinder {
  private final ExecutionRequestJpaRepository repo;

  @Override
  public boolean existsActiveRequest() {
    return repo.existsByStatusIn(List.of("REQUESTED", "RUNNING"));
  }

  @Override
  public Optional<ExecutionRequest> findById(Long id) {
    return repo.findById(id);
  }

  @Override
  public List<ExecutionRequest> findAllOrderByRequestedAtDesc() {
    return repo.findAllByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<ExecutionRequest> findRunning() {
    return repo.findFirstByStatusOrderByStartedAtDesc("RUNNING");
  }

  @Override
  public Optional<ExecutionRequest> findLatest() {
    return repo.findTopByOrderByRequestedAtDesc();
  }

  @Override
  public Optional<ExecutionRequest> findOldestRequested() {
    return repo.findFirstByStatusOrderByStartedAtDesc("REQUESTED");
  }

}
