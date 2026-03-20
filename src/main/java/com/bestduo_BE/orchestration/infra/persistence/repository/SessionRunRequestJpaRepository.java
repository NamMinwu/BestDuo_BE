package com.bestduo_BE.orchestration.infra.persistence.repository;

import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRunRequestJpaRepository extends JpaRepository<SessionRunRequest, Long> {
  boolean existsByStatusIn(List<String> statuses);

  Optional<SessionRunRequest> findFirstByStatusOrderByRequestedAtAsc(String status);

  List<SessionRunRequest> findAllByOrderByRequestedAtDesc();

  Optional<SessionRunRequest> findTopByOrderByRequestedAtDesc();

  Optional<SessionRunRequest> findFirstByStatusOrderByStartedAtDesc(String status);

}
