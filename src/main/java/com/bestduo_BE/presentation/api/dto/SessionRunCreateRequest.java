package com.bestduo_BE.presentation.api.dto;

import com.bestduo_BE.domain.model.Tier;

public record SessionRunCreateRequest(
    int budgetTotal,
    Double seedRatio,
    Double refreshRatio,
    int consumeLimitPerCycle,
    int maxConsumeCycles,
    Integer refreshLimit,
    Tier tier
) {
}
