package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.persistence.entity.SessionRunRequest;
import java.util.List;
import java.util.Optional;

public interface SessionRunRequestFinder {
  boolean existsActiveRequest();
  Optional<SessionRunRequest> findById(Long id);
  List<SessionRunRequest> findAllOrderByRequestedAtDesc();
  Optional<SessionRunRequest> findRunning();
  Optional<SessionRunRequest> findLatest();
  Optional<SessionRunRequest> findOldestRequested();
}
