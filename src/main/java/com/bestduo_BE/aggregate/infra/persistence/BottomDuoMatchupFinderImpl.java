package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BottomDuoMatchupFinderImpl implements BottomDuoMatchupFinder {

  private final BottomDuoMatchupAggregateJpaRepository repository;

  @Override
  public int findMyDuoTotalGames(Tier tier, String patchVersionOrNull, String myAdcChampionId, String mySupChampionId) {
    String patchVersion = resolvePatchVersion(patchVersionOrNull);
    if (patchVersion == null) {
      return 0;
    }
    return repository.sumGamesOfMyDuo(patchVersion, tier.name(), myAdcChampionId, mySupChampionId);
  }

  @Override
  public List<MatchupRow> findMatchups(
      Tier tier,
      String patchVersionOrNull,
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

    String patchVersion = resolvePatchVersion(patchVersionOrNull);
    if (patchVersion == null) {
      return List.of();
    }

    var entities = switch (sortKey) {
      case WINRATE_DESC -> repository.findTopWinRateDesc(patchVersion, tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, limit);
      case WINRATE_ASC  -> repository.findTopWinRateAsc(patchVersion, tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, limit);
      case PICKRATE_DESC -> repository.findTopPickRateDesc(patchVersion, tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, myTotalGamesForPickRate, limit);
      case PICKRATE_ASC  -> repository.findTopPickRateAsc(patchVersion, tier.name(), myAdcChampionId, mySupChampionId, oppAdc, oppSup, myTotalGamesForPickRate, limit);
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
      String patchVersionOrNull,
      String myAdcChampionId,
      String mySupChampionId,
      int maxRows
  ) {
    int limit = Math.max(1, maxRows);

    String patchVersion = resolvePatchVersion(patchVersionOrNull);
    if (patchVersion == null) {
      return List.of();
    }

    var entities = repository.findCountersByLowestWinRate(
        patchVersion,
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

  @Override
  public String resolvePatchVersion(String patchVersionOrNull) {
    String patchVersion = blankToNull(patchVersionOrNull);
    if (patchVersion != null) {
      return patchVersion;
    }
    return repository.findLatestPatchVersion();
  }
}
