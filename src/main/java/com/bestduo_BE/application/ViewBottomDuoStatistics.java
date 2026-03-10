package com.bestduo_BE.application;

import com.bestduo_BE.application.port.BottomDuoStatFinder;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoStatistics {

  private static final int MAX_ROWS = 1000;

  private final BottomDuoStatFinder statFinder;
  private final ChampionMetaClient championMetaClient;

  public BottomDuoStatisticsResponse execute(
      Tier tier,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      BottomDuoStatFinder.SortKey sortKey
  ) {
    int totalGames = statFinder.findTierTotalGames(tier);

    List<BottomDuoStatisticsResponse.Item> items =
        statFinder.findStats(
                tier,
                blankToNull(adcChampionIdOrNull),
                blankToNull(supChampionIdOrNull),
                sortKey,
                totalGames,
                MAX_ROWS
            ).stream()
            .map(row -> toItem(row, totalGames))
            .toList();

    return new BottomDuoStatisticsResponse(tier.name(), totalGames, items);
  }

  private BottomDuoStatisticsResponse.Item toItem(BottomDuoStatFinder.StatRow row, int totalGames) {
    var adc = championMetaClient.findById(row.adcChampionId());
    var sup = championMetaClient.findById(row.supChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) row.games() / totalGames;

    return new BottomDuoStatisticsResponse.Item(
        adc.name(),
        adc.imageUrl(),
        sup.name(),
        sup.imageUrl(),
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
