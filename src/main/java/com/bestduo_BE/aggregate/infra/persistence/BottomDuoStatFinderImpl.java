package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.application.port.BottomDuoStatFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BottomDuoStatFinderImpl implements BottomDuoStatFinder {
  private final BottomDuoStatAggregateJpaRepository repository;

  @Override
  public int findTierTotalGames(Tier tier, String patchVersionOrNull) {
    String patchVersion = resolvePatchVersion(patchVersionOrNull);
    if (patchVersion == null) {
      return 0;
    }
    return repository.sumGamesByTier(patchVersion, tier.name());
  }

  @Override
  public List<StatRow> findStats(Tier tier,
      String patchVersionOrNull,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      SortKey sortKey,
      int tierTotalGamesForPickRate,
      int maxRows) {

    int limit = Math.max(1, maxRows);

    String adc = blankToNull(adcChampionIdOrNull);
    String sup = blankToNull(supChampionIdOrNull);

    String patchVersion = resolvePatchVersion(patchVersionOrNull);
    if (patchVersion == null) {
      return List.of();
    }

    var entities = switch (sortKey) {
      case WINRATE_DESC -> repository.findTopWinRateDesc(patchVersion, tier.name(), adc, sup, limit);
      case WINRATE_ASC  -> repository.findTopWinRateAsc(patchVersion, tier.name(), adc, sup, limit);
      case PICKRATE_DESC -> repository.findTopPickRateDesc(patchVersion, tier.name(), adc, sup, tierTotalGamesForPickRate, limit);
      case PICKRATE_ASC  -> repository.findTopPickRateAsc(patchVersion, tier.name(), adc, sup, tierTotalGamesForPickRate, limit);
      case DUO_TIER_ASC -> repository.findTopDuoTierAsc(patchVersion, tier.name(), adc, sup, limit);
      case DUO_TIER_DESC -> repository.findTopDuoTierDesc(patchVersion, tier.name(), adc, sup, limit);
      case RANKING_ASC -> repository.findTopRankingAsc(patchVersion, tier.name(), adc, sup, limit);
      case RANKING_DESC -> repository.findTopRankingDesc(patchVersion, tier.name(), adc, sup, limit);
    };

    return entities.stream()
        .map(e -> new StatRow(
            e.getAdcChampionId(),
            e.getSupChampionId(),
            Tier.valueOf(e.getTier()),
            e.getWins(),
            e.getGames(),
            e.getDuoTier(),
            e.getRanking(),
            e.getRankDelta()
        ))
        .toList();
  }

  private String blankToNull(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }

  @Override
  public String resolvePatchVersion(String patchVersionOrNull) {
    return doResolvePatchVersion(patchVersionOrNull);
  }

  private String doResolvePatchVersion(String patchVersionOrNull) {
    String patchVersion = blankToNull(patchVersionOrNull);
    if (patchVersion != null) {
      return patchVersion;
    }
    return repository.findLatestPatchVersion();
  }

}
