package com.bestduo_BE.presentation.api.dto;

public record SessionRunCreateRequest(
    int budgetTotal,
    Double seedRatio,
    Double refreshRatio,
    int consumeLimitPerCycle,
    int maxConsumeCycles
) {

}
