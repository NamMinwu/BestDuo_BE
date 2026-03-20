package com.bestduo_BE.aggregate.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;

public interface BottomDuoMatchupFinder {

  enum SortKey {
    PICKRATE_ASC, PICKRATE_DESC,
    WINRATE_ASC,  WINRATE_DESC
  }

  record MatchupRow(
      String myAdcChampionId,
      String mySupChampionId,
      String oppAdcChampionId,
      String oppSupChampionId,
      Tier tier,
      int wins,
      int games
  ) {
    public double winRate() {
      return games == 0 ? 0 : (double) wins / games;
    }
  }

  int findMyDuoTotalGames(Tier tier, String patchVersionOrNull, String myAdcChampionId, String mySupChampionId);

  List<MatchupRow> findMatchups(
      Tier tier,
      String patchVersionOrNull,
      String myAdcChampionId,
      String mySupChampionId,
      String oppAdcChampionIdOrNull,
      String oppSupChampionIdOrNull,
      SortKey sortKey,
      int myTotalGamesForPickRate,
      int maxRows
  );

  // ✅ 추가: 카운터(최저 승률 N개)
  List<MatchupRow> findCountersByLowestWinRate(
      Tier tier,
      String patchVersionOrNull,
      String myAdcChampionId,
      String mySupChampionId,
      int maxRows
  );

  String resolvePatchVersion(String patchVersionOrNull);
}
