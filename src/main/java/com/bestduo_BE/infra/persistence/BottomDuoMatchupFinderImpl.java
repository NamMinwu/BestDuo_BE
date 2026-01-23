package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.repository.BottomDuoMatchupAggJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BottomDuoMatchupFinderImpl implements BottomDuoMatchupFinder {

  private final BottomDuoMatchupAggJpaRepository repository;

  @Override
  public int findMyDuoTotalGames(Tier tier, String myAdcChampionId, String mySupChampionId) {
    return repository.sumGamesOfMyDuo(tier.name(), myAdcChampionId, mySupChampionId);
  }

  @Override
  public List<MatchupRow> findMatchups(
      Tier tier,
      String myAdcChampionId,
      String mySupChampionId,
      String oppAdcChampionIdOrNull,
      String oppSupChampionIdOrNull,
      SortKey sortKey,
      int myTotalGamesForPickRate,
      int maxRows
  ) {
    int limit = Math.max(1, maxRows);

    String oppAdc = blankToNull(oppAdcChampionIdOrNull);
    String oppSup = blankToNull(oppSupChampionIdOrNull);

    var entities = switch (sortKey) {
      case WINRATE_DESC -> repository.findTopWinRateDesc(tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, limit);
      case WINRATE_ASC  -> repository.findTopWinRateAsc(tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, limit);
      case PICKRATE_DESC -> repository.findTopPickRateDesc(tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, myTotalGamesForPickRate, limit);
      case PICKRATE_ASC  -> repository.findTopPickRateAsc(tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, myTotalGamesForPickRate, limit);
    };

    return entities.stream()
        .map(e -> new MatchupRow(
            e.getMyAdcChampionId(),
            e.getMySupChampionId(),
            e.getOppAdcChampionId(),
            e.getOppSupChampionId(),
            Tier.valueOf(e.getTier()),
            e.getWins(),
            e.getGames()
        ))
        .toList();
  }

  @Override
  public List<MatchupRow> findCountersByLowestWinRate(
      Tier tier,
      String myAdcChampionId,
      String mySupChampionId,
      int maxRows
  ) {
    int limit = Math.max(1, maxRows);

    var entities = repository.findCountersByLowestWinRate(
        tier.name(),
        myAdcChampionId,
        mySupChampionId,
        limit
    );

    return entities.stream()
        .map(e -> new MatchupRow(
            e.getMyAdcChampionId(),
            e.getMySupChampionId(),
            e.getOppAdcChampionId(),
            e.getOppSupChampionId(),
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
