package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.Tier;
import java.util.List;

public interface BottomDuoStatFinder {

  enum SortKey {
    PICKRATE_ASC, PICKRATE_DESC,
    WINRATE_ASC,  WINRATE_DESC,
    DUO_TIER_ASC, DUO_TIER_DESC,
    RANKING_ASC, RANKING_DESC
  }

  record StatRow(
      String adcChampionId,
      String supChampionId,
      Tier tier,
      int wins,
      int games,
      Integer duoTier,
      Integer ranking,
      Integer rankDelta
  ) {
    public double winRate() {
      return games == 0 ? 0 : (double) wins / games;
    }
  }

  int findTierTotalGames(Tier tier, String patchVersionOrNull);

  List<StatRow> findStats(Tier tier,
      String patchVersionOrNull,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      SortKey sortKey,
      int tierTotalGamesForPickRate,
      int maxRows);
}
