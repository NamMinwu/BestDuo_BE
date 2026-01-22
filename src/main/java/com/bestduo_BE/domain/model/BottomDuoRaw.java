package com.bestduo_BE.domain.model;

public record BottomDuoRaw(
    String matchId,        // KR_1234567890
    int teamId,            // 100 or 200
    int adcChampionId,     // 예: 222 (Jinx)
    int supChampionId,     // 예: 412 (Thresh)
    boolean win,           // 이 팀이 이겼는지
    String patch,          // "15.23"
    Tier tier    // EMERALD_PLUS (collection context)
) {
}
