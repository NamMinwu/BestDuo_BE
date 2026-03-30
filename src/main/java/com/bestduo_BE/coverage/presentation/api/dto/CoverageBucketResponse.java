package com.bestduo_BE.coverage.presentation.api.dto;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;

public record CoverageBucketResponse(
    Long id,
    String patch,
    Tier tier,
    long targetMatchCount,
    long currentMatchCount,
    long deficitMatchCount,
    String status,
    int priority,
    OffsetDateTime lastEvaluatedAt
) {
}
