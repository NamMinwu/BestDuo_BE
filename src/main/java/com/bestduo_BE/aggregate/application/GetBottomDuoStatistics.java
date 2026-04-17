package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatFinder;
import com.bestduo_BE.common.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoStatisticsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetBottomDuoStatistics {

  private static final int MAX_ROWS = 1000;

  private final BottomDuoStatFinder statFinder;
  private final ChampionMetaClient championMetaClient;

  public BottomDuoStatisticsResponse execute(
      Tier tier,
      String patchVersionOrNull,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      BottomDuoStatFinder.SortKey sortKey
  ) {
    String patchVersion = blankToNull(patchVersionOrNull);
    String adcChampionId = blankToNull(adcChampionIdOrNull);
    String supChampionId = blankToNull(supChampionIdOrNull);

    String resolvedPatchVersion = statFinder.resolvePatchVersion(patchVersion);
    int totalGames = statFinder.findTierTotalGames(tier, resolvedPatchVersion);

    List<BottomDuoStatisticsResponse.Item> items =
        statFinder.findStats(
                tier,
                resolvedPatchVersion,
                adcChampionId,
                supChampionId,
                sortKey,
                totalGames,
                MAX_ROWS
            ).stream()
            .map(row -> toItem(row, totalGames))
            .toList();

    return new BottomDuoStatisticsResponse(tier.name(), resolvedPatchVersion, totalGames, items);
  }

  private BottomDuoStatisticsResponse.Item toItem(BottomDuoStatFinder.StatRow row, int totalGames) {
    var adc = championMetaClient.findById(row.adcChampionId());
    var sup = championMetaClient.findById(row.supChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) row.games() / totalGames;

    return new BottomDuoStatisticsResponse.Item(
        row.adcChampionId(),
        adc.name(),
        adc.imageUrl(),
        row.supChampionId(),
        sup.name(),
        sup.imageUrl(),
        row.winRate(),
        pickRate,
        row.games(),
        row.duoTier(),
        row.ranking(),
        row.rankDelta()
    );
  }

  private String blankToNull(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }
}
