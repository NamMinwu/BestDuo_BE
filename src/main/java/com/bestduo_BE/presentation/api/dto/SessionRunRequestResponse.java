package com.bestduo_BE.presentation.api.dto;

import java.time.OffsetDateTime;

public record SessionRunRequestResponse(
    Long id,
    String status,
    int budgetTotal,
    double seedRatio,
    double refreshRatio,
    int consumeLimitPerCycle,
    int maxConsumeCycles,
    OffsetDateTime requestedAt,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    String message
) {
}
