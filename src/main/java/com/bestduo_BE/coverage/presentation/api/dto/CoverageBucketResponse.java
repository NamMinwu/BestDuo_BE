package com.bestduo_BE.coverage.presentation.api.dto;

import com.bestduo_BE.common.domain.model.Tier;

public record CoverageBucketResponse(
    Long id,
    String patch,
    Tier tier
) {
}
