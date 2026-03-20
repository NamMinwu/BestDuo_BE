package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.SessionRunRequestFinder;
import com.bestduo_BE.orchestration.application.port.SessionRunRequestSaver;
import com.bestduo_BE.config.DailySessionProperties;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.infra.persistence.entity.SessionRunRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.SessionRunCreateRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.SessionRunRequestResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionRunRequestService {

  private final SessionRunRequestFinder finder;
  private final SessionRunRequestSaver saver;
  private final DailySessionProperties properties;

  @Transactional
  public SessionRunRequestResponse create(SessionRunCreateRequest request) {
    if (finder.existsActiveRequest()) {
      throw new IllegalStateException("Session already REQUESTED or RUNNING.");
    }

    double seedRatio = request.seedRatio() != null
        ? request.seedRatio()
        : properties.getSeedRatio();

    double refreshRatio = request.refreshRatio() != null
        ? request.refreshRatio()
        : properties.getRefreshRatio();

    int refreshLimit = request.refreshLimit() != null
        ? request.refreshLimit()
        : properties.getRefreshLimit();

    Tier tier = request.tier() != null
        ? request.tier()
        : properties.getSeed().getSeedTier();

    SessionRunRequest saved = saver.save(
        SessionRunRequest.newRequested(
            request.budgetTotal(),
            seedRatio,
            refreshRatio,
            request.consumeLimitPerCycle(),
            request.maxConsumeCycles(),
            refreshLimit,
            tier
        )
    );

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public SessionRunRequestResponse get(Long id) {
    SessionRunRequest request = finder.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Session run request not found. id=" + id));
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public List<SessionRunRequestResponse> getAll() {
    return finder.findAllOrderByRequestedAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public SessionRunRequestResponse getRunning() {
    return finder.findRunning()
        .map(this::toResponse)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public SessionRunRequestResponse getLatest() {
    return finder.findLatest()
        .map(this::toResponse)
        .orElse(null);
  }

  private SessionRunRequestResponse toResponse(SessionRunRequest r) {
    return new SessionRunRequestResponse(
        r.getId(),
        r.getStatus(),
        r.getBudgetTotal(),
        r.getSeedRatio(),
        r.getRefreshRatio(),
        r.getConsumeLimitPerCycle(),
        r.getMaxConsumeCycles(),
        r.getRefreshLimit(),
        r.getTier(),
        r.getRequestedAt(),
        r.getStartedAt(),
        r.getEndedAt(),
        r.getMessage()
    );
  }
}
