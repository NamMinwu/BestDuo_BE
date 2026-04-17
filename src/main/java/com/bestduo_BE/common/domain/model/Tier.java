package com.bestduo_BE.common.domain.model;

import java.util.List;

public enum Tier {
  CHALLENGER,
  GRANDMASTER,
  MASTER,
  DIAMOND,
  EMERALD,
  PLATINUM,
  GOLD,
  SILVER,
  BRONZE,
  IRON,
  ALL_TIERS;

  public static final List<Tier> APEX_TIERS = List.of(CHALLENGER, GRANDMASTER, MASTER);

  public boolean isApex() {
    return APEX_TIERS.contains(this);
  }
}
