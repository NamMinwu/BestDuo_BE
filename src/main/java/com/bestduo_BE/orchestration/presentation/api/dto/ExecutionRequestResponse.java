package com.bestduo_BE.orchestration.presentation.api.dto;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;

public record ExecutionRequestResponse(
    Long id,
    String status,
    int budgetTotal,
    double seedRatio,
    double refreshRatio,
    int ingestLimitPerCycle,
    int maxIngestCycles,
    int refreshLimit,
    Tier tier,
    OffsetDateTime requestedAt,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    String message
) {
}
