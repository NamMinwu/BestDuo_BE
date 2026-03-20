package com.bestduo_BE.orchestration.application.port;

import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import java.util.List;
import java.util.Optional;

public interface RunRequestFinder {
  boolean existsActiveRequest();
  Optional<RunRequest> findById(Long id);
  List<RunRequest> findAllOrderByRequestedAtDesc();
  Optional<RunRequest> findRunning();
  Optional<RunRequest> findLatest();
  Optional<RunRequest> findOldestRequested();
}
