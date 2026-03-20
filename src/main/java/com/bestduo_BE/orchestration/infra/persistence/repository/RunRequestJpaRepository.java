package com.bestduo_BE.orchestration.infra.persistence.repository;

import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRequestJpaRepository extends JpaRepository<RunRequest, Long> {
  boolean existsByStatusIn(List<String> statuses);

  Optional<RunRequest> findFirstByStatusOrderByRequestedAtAsc(String status);

  List<RunRequest> findAllByOrderByRequestedAtDesc();

  Optional<RunRequest> findTopByOrderByRequestedAtDesc();

  Optional<RunRequest> findFirstByStatusOrderByStartedAtDesc(String status);

}
