package com.bestduo_BE.presentation.api.dto;

import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;

public record BottomDuoDetailStatisticsRequest(
    String adChampionId,
    String supChampionId,
    Tier tier,
    SortOption sortOption
) {}
