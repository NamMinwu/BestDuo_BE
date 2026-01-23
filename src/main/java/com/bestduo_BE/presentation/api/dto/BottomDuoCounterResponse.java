package com.bestduo_BE.presentation.api.dto;

import java.util.List;

public record BottomDuoCounterResponse(
    String tier,
    int totalGames,
    DuoMeta myDuo,
    int counterSize,
    List<Item> counters
) {
  public record DuoMeta(
      String adcName,
      String adcImage,
      String supName,
      String supImage
  ) {}

  public record Item(
      String oppAdcName,
      String oppAdcImage,
      String oppSupName,
      String oppSupImage,
      double winRate,
      double pickRate,
      int games
  ) {}
}
