package com.bestduo_BE.domain.model;
import lombok.Data;

@Data
public class BottomDuoStat {
  private final String adChampionId;
  private final String supChampionId;
  private final Tier tier;
  private final int wins;
  private final int games;

  public double getWinRate() { return games == 0 ? 0 : (double) wins / games; }
}
