package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.orchestration.application.port.ExecutionRequestFinder;
import com.bestduo_BE.orchestration.application.port.ExecutionRequestSaver;
import com.bestduo_BE.config.DailyRunProperties;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.ExecutionCreateRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.ExecutionRequestResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionRequestService {

  private final ExecutionRequestFinder finder;
  private final ExecutionRequestSaver saver;
  private final DailyRunProperties properties;

  @Transactional
  public ExecutionRequestResponse create(ExecutionCreateRequest request) {
    if (finder.existsActiveRequest()) {
      throw new IllegalStateException("Execution already REQUESTED or RUNNING.");
    }

    RequestAdjust requestAdjust = adjustRequest(request);
    ExecutionRequest executionRequest = createExecutionRequest(request, requestAdjust);
    ExecutionRequest saved = saver.save(executionRequest);

    return toResponse(saved);
  }

  private ExecutionRequest createExecutionRequest(ExecutionCreateRequest request,
      RequestAdjust requestAdjust) {
    return ExecutionRequest.newRequested(
        request.budgetTotal(),
        requestAdjust.seedRatio(),
        requestAdjust.refreshRatio(),
        request.ingestLimitPerCycle(),
        request.maxIngestCycles(),
        requestAdjust.refreshLimit(),
        requestAdjust.tier()
    );
  }

  private RequestAdjust adjustRequest(ExecutionCreateRequest request) {
    double seedRatio = resolveSeedRatio(request);
    double refreshRatio = resolveRefreshRatio(request);
    int refreshLimit = resolveRefreshLimit(request);
    Tier tier = resolveTier(request);

    return new RequestAdjust(seedRatio, refreshRatio, refreshLimit, tier);
  }

  private double resolveSeedRatio(ExecutionCreateRequest request) {
    return request.seedRatio() != null ? request.seedRatio() : properties.getSeedRatio();
  }

  // TODO Phase 2: refresh 제거 후 삭제 — ExecutionOrchestrator에서 실제로 사용하지 않음
  private double resolveRefreshRatio(ExecutionCreateRequest request) {
    return request.refreshRatio() != null ? request.refreshRatio() : properties.getRefreshRatio();
  }

  // TODO Phase 2: refresh 제거 후 삭제 — ExecutionOrchestrator에서 실제로 사용하지 않음
  private int resolveRefreshLimit(ExecutionCreateRequest request) {
    return request.refreshLimit() != null ? request.refreshLimit() : properties.getRefreshLimit();
  }

  private Tier resolveTier(ExecutionCreateRequest request) {
    return request.tier() != null ? request.tier() : properties.getSeed().getSeedTier();
  }

  private record RequestAdjust(double seedRatio, double refreshRatio, int refreshLimit, Tier tier) {}

  @Transactional(readOnly = true)
  public ExecutionRequestResponse get(Long id) {
    ExecutionRequest request = finder.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Execution request not found. id=" + id));
    return toResponse(request);
  }

  @Transactional(readOnly = true)
  public List<ExecutionRequestResponse> getAll() {
    return finder.findAllOrderByRequestedAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ExecutionRequestResponse getRunning() {
    return finder.findRunning()
        .map(this::toResponse)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public ExecutionRequestResponse getLatest() {
    return finder.findLatest()
        .map(this::toResponse)
        .orElse(null);
  }

  private ExecutionRequestResponse toResponse(ExecutionRequest r) {
    return new ExecutionRequestResponse(
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
