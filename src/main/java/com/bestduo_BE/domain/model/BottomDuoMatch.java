package com.bestduo_BE.domain.model;

import lombok.Data;

@Data
public class BottomDuoMatch {
  private final String adChampionId;
  private final String supChampionId;
  private final Tier tier;
  private final boolean win;
}
