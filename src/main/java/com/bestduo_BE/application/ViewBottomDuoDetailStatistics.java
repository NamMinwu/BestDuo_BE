package com.bestduo_BE.application;

import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoDetailStatistics {

  private static final int MAX_ROWS = 100;

  private final BottomDuoMatchupFinder matchupFinder;
  private final ChampionMetaClient championMetaClient;

  public BottomDuoDetailStatisticsResponse execute(
      Tier tier,
      String myAdcChampionId,
      String mySupChampionId,
      String oppAdcChampionIdOrNull,
      String oppSupChampionIdOrNull,
      BottomDuoMatchupFinder.SortKey sortKey
  ) {
    int totalGames = matchupFinder.findMyDuoTotalGames(tier, myAdcChampionId, mySupChampionId);

    var myAdc = championMetaClient.findById(myAdcChampionId);
    var mySup = championMetaClient.findById(mySupChampionId);

    var myMeta = new BottomDuoDetailStatisticsResponse.DuoMeta(
        myAdcChampionId,
        myAdc.name(),
        myAdc.imageUrl(),
        mySupChampionId,
        mySup.name(),
        mySup.imageUrl()
    );

    List<BottomDuoDetailStatisticsResponse.Item> items =
        matchupFinder.findMatchups(
                tier,
                myAdcChampionId,
                mySupChampionId,
                blankToNull(oppAdcChampionIdOrNull),
                blankToNull(oppSupChampionIdOrNull),
                sortKey,
                totalGames,
                MAX_ROWS
            ).stream()
            .map(row -> toItem(row, totalGames))
            .toList();

    return new BottomDuoDetailStatisticsResponse(tier.name(), totalGames, myMeta, items);
  }

  private BottomDuoDetailStatisticsResponse.Item toItem(BottomDuoMatchupFinder.MatchupRow row, int totalGames) {
    var oppAdc = championMetaClient.findById(row.oppAdcChampionId());
    var oppSup = championMetaClient.findById(row.oppSupChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) row.games() / totalGames;

    return new BottomDuoDetailStatisticsResponse.Item(
        row.oppAdcChampionId(),
        oppAdc.name(),
        oppAdc.imageUrl(),
        row.oppSupChampionId(),
        oppSup.name(),
        oppSup.imageUrl(),
        row.winRate(),
        pickRate,
        row.games()
    );
  }

  private String blankToNull(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }
}
