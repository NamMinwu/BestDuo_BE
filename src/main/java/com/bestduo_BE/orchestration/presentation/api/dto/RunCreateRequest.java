package com.bestduo_BE.orchestration.presentation.api.dto;

import com.bestduo_BE.common.domain.model.Tier;

public record RunCreateRequest(
    int budgetTotal,
    Double seedRatio,
    Double refreshRatio,
    int ingestLimitPerCycle,
    int maxIngestCycles,
    Integer refreshLimit,
    Tier tier
) {
}
