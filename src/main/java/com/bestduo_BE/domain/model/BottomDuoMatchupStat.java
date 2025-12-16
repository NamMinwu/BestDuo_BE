package com.bestduo_BE.domain.model;

import lombok.Data;

@Data
public class BottomDuoMatchupStat {
  private final String myAdId;
  private final String mySupId;
  private final String opponentAdId;
  private final String opponentSupId;
  private final Tier tier;
  private final int wins;
  private final int games;

  public double getWinRate() { return games == 0 ? 0 : (double) wins / games; }

}
