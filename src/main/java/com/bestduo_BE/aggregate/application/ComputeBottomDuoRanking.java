package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAgg;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ComputeBottomDuoRanking {

  private static final int MIN_GAMES = 4;
  private static final int INSUFFICIENT_TIER = 5;

  private final BottomDuoStatAggJpaRepository repository;

  @Transactional
  public Result execute() {
    String currentPatch = repository.findLatestPatchVersion();
    if (currentPatch == null) {
      return new Result(null, 0);
    }

    List<BottomDuoStatAgg> currentStats = repository.findByPatchVersion(currentPatch);
    if (currentStats.isEmpty()) {
      return new Result(currentPatch, 0);
    }

    Map<String, Integer> totalGamesByTier = currentStats.stream()
        .collect(Collectors.groupingBy(BottomDuoStatAgg::getTier, Collectors.summingInt(BottomDuoStatAgg::getGames)));

    Map<String, Integer> previousRankings = loadPreviousRankings(currentPatch);
    OffsetDateTime now = OffsetDateTime.now();

    Map<String, List<BottomDuoStatAgg>> byTier = currentStats.stream()
        .collect(Collectors.groupingBy(BottomDuoStatAgg::getTier));

    int updatedRows = 0;

    for (Map.Entry<String, List<BottomDuoStatAgg>> entry : byTier.entrySet()) {
      String tier = entry.getKey();
      int tierTotalGames = totalGamesByTier.getOrDefault(tier, 0);
      List<Candidate> eligible = new ArrayList<>();

      for (BottomDuoStatAgg agg : entry.getValue()) {
        double pickRate = tierTotalGames == 0 ? 0 : (double) agg.getGames() / tierTotalGames;
        boolean enoughSamples = agg.getGames() >= MIN_GAMES;
        if (!enoughSamples) {
          agg.markInsufficientData(pickRate, INSUFFICIENT_TIER, now);
          updatedRows++;
          continue;
        }
        double rankScore = computeRankScore(agg, pickRate);
        eligible.add(new Candidate(agg, pickRate, rankScore));
      }

      eligible.sort(Comparator
          .comparing(Candidate::rankScore).reversed()
          .thenComparing(c -> c.agg().getGames(), Comparator.reverseOrder())
      );

      int ranking = 1;
      for (Candidate candidate : eligible) {
        BottomDuoStatAgg agg = candidate.agg();
        Integer previousRanking = previousRankings.get(agg.duoKey());
        Integer rankDelta = previousRanking == null ? null : previousRanking - ranking;
        int duoTier = toDuoTier(candidate.rankScore());
        agg.applyRankingMetrics(
            candidate.pickRate(),
            candidate.rankScore(),
            ranking,
            duoTier,
            previousRanking,
            rankDelta,
            now
        );
        ranking++;
        updatedRows++;
      }
    }

    repository.saveAll(currentStats);
    return new Result(currentPatch, updatedRows);
  }

  private Map<String, Integer> loadPreviousRankings(String currentPatch) {
    String previousPatch = repository.findPreviousPatchVersion(currentPatch);
    if (previousPatch == null) {
      return Map.of();
    }
    return repository.findByPatchVersion(previousPatch).stream()
        .filter(e -> e.getRanking() != null)
        .collect(Collectors.toMap(BottomDuoStatAgg::duoKey, BottomDuoStatAgg::getRanking, (left, right) -> left, HashMap::new));
  }

  private double computeRankScore(BottomDuoStatAgg agg, double pickRate) {
    double winScore = agg.getAdjustedWinRate();
    double pickScore = pickRate;
    double gameScore = Math.min(1.0, (double) agg.getGames() / MIN_GAMES);
    return 0.60 * winScore + 0.25 * pickScore + 0.15 * gameScore;
  }

  private int toDuoTier(double score) {
    if (score >= 0.75) return 0;
    if (score >= 0.65) return 1;
    if (score >= 0.55) return 2;
    if (score >= 0.45) return 3;
    if (score >= 0.35) return 4;
    return 5;
  }

  public record Result(String patchVersion, int updatedRows) {}

  private record Candidate(BottomDuoStatAgg agg, double pickRate, double rankScore) {}
}
