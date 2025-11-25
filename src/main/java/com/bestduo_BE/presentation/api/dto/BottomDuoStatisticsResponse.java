package com.bestduo_BE.presentation.api.dto;

import java.util.List;

public record BottomDuoStatisticsResponse(
    int totalGames,
    List<BottomDuoStatView> views
) {
  public record BottomDuoStatView(
      String adName,
      String adImage,
      String supName,
      String supImage,
      double winRate,
      int games,
      double pickRate
  ) {}
}
