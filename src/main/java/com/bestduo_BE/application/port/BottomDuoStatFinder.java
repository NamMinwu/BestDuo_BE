package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.Tier;
import java.util.List;

public interface BottomDuoStatFinder {

  enum SortKey {
    PICKRATE_ASC, PICKRATE_DESC,
    WINRATE_ASC,  WINRATE_DESC
  }

  record StatRow(
      String adcChampionId,
      String supChampionId,
      Tier tier,
      int wins,
      int games
  ) {
    public double winRate() {
      return games == 0 ? 0 : (double) wins / games;
    }
  }

  int findTierTotalGames(Tier tier);

  List<StatRow> findStats(Tier tier,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      SortKey sortKey,
      int tierTotalGamesForPickRate,
      int maxRows);
}