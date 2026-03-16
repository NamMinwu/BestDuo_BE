package com.bestduo_BE.domain.model;

public record SeedBootstrapCommand(
    String queue,
    String tier,
    String division,
    Tier seedTier, // bottom_duo_raw.collection_tier로 들어갈 값
    int startPage,
    int endPage,
    int matchesPerPuuid,
    int maxEntries
) {

}
