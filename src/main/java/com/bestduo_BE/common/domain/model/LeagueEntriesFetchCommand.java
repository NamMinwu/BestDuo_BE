package com.bestduo_BE.common.domain.model;

public record LeagueEntriesFetchCommand(
    String queue,
    String tier,
    String division,
    Tier seedTier, // bottom_duo_raw.collection_tier로 들어갈 값
    int page,
    int maxEntries
) {

}
