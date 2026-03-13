package com.bestduo_BE.presentation.api.dto;

import com.bestduo_BE.domain.model.Tier;
import java.time.OffsetDateTime;

public record SessionRunRequestResponse(
    Long id,
    String status,
    int budgetTotal,
    double seedRatio,
    double refreshRatio,
    int consumeLimitPerCycle,
    int maxConsumeCycles,
    int refreshLimit,
    Tier tier,
    OffsetDateTime requestedAt,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    String message
) {
}
