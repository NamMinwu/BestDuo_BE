package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.RunRequestFinder;
import com.bestduo_BE.orchestration.application.port.RunRequestSaver;
import com.bestduo_BE.config.DailyRunProperties;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.RunCreateRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.RunRequestResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RunRequestService {

  private final RunRequestFinder finder;
  private final RunRequestSaver saver;
  private final DailyRunProperties properties;

  @Transactional
  public RunRequestResponse create(RunCreateRequest request) {
    if (finder.existsActiveRequest()) {
      throw new IllegalStateException("Run already REQUESTED or RUNNING.");
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

    RunRequest saved = saver.save(
        RunRequest.newRequested(
            request.budgetTotal(),
            seedRatio,
            refreshRatio,
            request.ingestLimitPerCycle(),
            request.maxIngestCycles(),
            refreshLimit,
            tier
        )
    );

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public RunRequestResponse get(Long id) {
    RunRequest request = finder.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Run request not found. id=" + id));
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public List<RunRequestResponse> getAll() {
    return finder.findAllOrderByRequestedAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public RunRequestResponse getRunning() {
    return finder.findRunning()
        .map(this::toResponse)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public RunRequestResponse getLatest() {
    return finder.findLatest()
        .map(this::toResponse)
        .orElse(null);
  }

  private RunRequestResponse toResponse(RunRequest r) {
    return new RunRequestResponse(
        r.getId(),
        r.getStatus(),
        r.getBudgetTotal(),
        r.getSeedRatio(),
        r.getRefreshRatio(),
        r.getIngestLimitPerCycle(),
        r.getMaxIngestCycles(),
        r.getRefreshLimit(),
        r.getTier(),
        r.getRequestedAt(),
        r.getStartedAt(),
        r.getEndedAt(),
        r.getMessage()
    );
  }
}
