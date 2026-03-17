package com.bestduo_BE.presentation.api.dto;

import java.util.List;

public record BottomDuoStatisticsResponse(
    String tier,
    int totalGames,
    List<Item> items
) {
  public record Item(
      String adcId,
      String adcName,
      String adcImage,
      String supId,
      String supName,
      String supImage,
      double winRate,
      double pickRate,
      int games,
      Integer duoTier,
      Integer ranking,
      Integer rankDelta
  ) {}
}
