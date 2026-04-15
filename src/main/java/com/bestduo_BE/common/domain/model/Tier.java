package com.bestduo_BE.common.domain.model;

import java.util.Set;

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

  public static final Set<Tier> APEX_TIERS = Set.of(CHALLENGER, GRANDMASTER, MASTER);

  public boolean isApex() {
    return APEX_TIERS.contains(this);
  }
}
