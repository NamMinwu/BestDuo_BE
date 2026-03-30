package com.bestduo_BE.coverage.presentation.api.dto;

import com.bestduo_BE.common.domain.model.Tier;

public record CoverageBucketCreateRequest(
    String patch,
    Tier tier,
    long targetMatchCount,
    Integer priority
) {
}
