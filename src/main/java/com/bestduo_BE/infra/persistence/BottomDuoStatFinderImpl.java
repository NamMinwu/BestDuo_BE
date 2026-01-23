package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.BottomDuoStatFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BottomDuoStatFinderImpl implements BottomDuoStatFinder {
  private final BottomDuoStatAggJpaRepository repository;

  @Override
  public int findTierTotalGames(Tier tier) {
    return repository.sumGamesByTier(tier.name());
  }

  @Override
  public List<StatRow> findStats(Tier tier,
      String adcChampionIdOrNull,
      String supChampionIdOrNull,
      SortKey sortKey,
      int tierTotalGamesForPickRate,
      int maxRows) {

    int limit = Math.max(1, maxRows);

    String adc = blankToNull(adcChampionIdOrNull);
    String sup = blankToNull(supChampionIdOrNull);

    var entities = switch (sortKey) {
      case WINRATE_DESC -> repository.findTopWinRateDesc(tier.name(), adc, sup, limit);
      case WINRATE_ASC  -> repository.findTopWinRateAsc(tier.name(), adc, sup, limit);
      case PICKRATE_DESC -> repository.findTopPickRateDesc(tier.name(), adc, sup, tierTotalGamesForPickRate, limit);
      case PICKRATE_ASC  -> repository.findTopPickRateAsc(tier.name(), adc, sup, tierTotalGamesForPickRate, limit);
    };

    return entities.stream()
        .map(e -> new StatRow(
            e.getAdcChampionId(),
            e.getSupChampionId(),
            Tier.valueOf(e.getTier()),
            e.getWins(),
            e.getGames()
        ))
        .toList();
  }

  private String blankToNull(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }

}
