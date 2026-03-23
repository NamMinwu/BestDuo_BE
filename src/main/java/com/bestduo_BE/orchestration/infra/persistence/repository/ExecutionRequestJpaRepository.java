package com.bestduo_BE.orchestration.infra.persistence.repository;

import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRequestJpaRepository extends JpaRepository<ExecutionRequest, Long> {
  boolean existsByStatusIn(List<String> statuses);

  Optional<ExecutionRequest> findFirstByStatusOrderByRequestedAtAsc(String status);

  List<ExecutionRequest> findAllByOrderByRequestedAtDesc();

  Optional<ExecutionRequest> findTopByOrderByRequestedAtDesc();

  Optional<ExecutionRequest> findFirstByStatusOrderByStartedAtDesc(String status);

}
