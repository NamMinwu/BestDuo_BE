package com.bestduo_BE.domain.model;

import lombok.Data;

@Data
public class BottomDuoFilterCriteria {
  private final String adCampionId;        // nullable
  private final String supCampionId;       // nullable
  private final Tier tierRange;  // nullable
  private final SortOption sortOption; // WIN_RATE, PICK_RATE, GAMES, ...
}