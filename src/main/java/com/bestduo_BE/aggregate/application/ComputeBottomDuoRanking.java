package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregate;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
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

  private final BottomDuoStatAggregateJpaRepository repository;

  @Transactional
  public Result execute(String patchVersion, String tier) {
    if (patchVersion == null) {
      return new Result(null, 0);
    }

    List<BottomDuoStatAggregate> currentStats = loadCurrentStats(patchVersion, tier);
    if (currentStats.isEmpty()) {
      return new Result(patchVersion, 0);
    }

    Map<String, Integer> totalGamesByTier = calculateTotalGamesByTier(currentStats);
    Map<String, Integer> previousRankings = loadPreviousRankings(patchVersion, tier);
    Map<String, List<BottomDuoStatAggregate>> statsByTier = groupStatsByTier(currentStats);

    int updatedRows = applyRankings(statsByTier, totalGamesByTier, previousRankings);

    repository.saveAll(currentStats);
    return new Result(patchVersion, updatedRows);
  }

  private int applyRankings(Map<String, List<BottomDuoStatAggregate>> statsByTier,
      Map<String, Integer> totalGamesByTier,
      Map<String, Integer> previousRankings) {
    int updatedRows = 0;
    OffsetDateTime now = OffsetDateTime.now();

    for (Map.Entry<String, List<BottomDuoStatAggregate>> entry : statsByTier.entrySet()) {
      String tier = entry.getKey();
      int tierTotalGames = totalGamesByTier.getOrDefault(tier, 0);
      int tierMaxGames = entry.getValue().stream()
          .mapToInt(BottomDuoStatAggregate::getGames)
          .max()
          .orElse(0);
      CandidateBuildResult candidates = buildEligibleCandidates(
          entry.getValue(), tierTotalGames, tierMaxGames, now);
      updatedRows += candidates.insufficientRows();
      updatedRows += applyRankingsForTier(candidates.eligible(), previousRankings, now);
    }

    return updatedRows;
  }

  private CandidateBuildResult buildEligibleCandidates(List<BottomDuoStatAggregate> tierStats,
      int tierTotalGames,
      int tierMaxGames,
      OffsetDateTime now) {
    List<Candidate> eligible = new ArrayList<>();
    int insufficientRows = 0;

    for (BottomDuoStatAggregate agg : tierStats) {
      double pickRate = tierTotalGames == 0 ? 0 : (double) agg.getGames() / tierTotalGames;
      boolean enoughSamples = agg.getGames() >= MIN_GAMES;
      if (!enoughSamples) {
        agg.markInsufficientData(pickRate, INSUFFICIENT_TIER, now);
        insufficientRows++;
        continue;
      }

      double rankScore = computeRankScore(agg, pickRate, tierMaxGames);
      eligible.add(new Candidate(agg, pickRate, rankScore));
    }

    return new CandidateBuildResult(eligible, insufficientRows);
  }

  private int applyRankingsForTier(List<Candidate> eligible,
      Map<String, Integer> previousRankings,
      OffsetDateTime now) {
    eligible.sort(Comparator
        .comparing(Candidate::rankScore).reversed()
        .thenComparing(c -> c.agg().getGames(), Comparator.reverseOrder())
    );

    int ranking = 1;
    int updatedRows = 0;
    for (Candidate candidate : eligible) {
      applyRanking(candidate, ranking, previousRankings, now);
      ranking++;
      updatedRows++;
    }

    return updatedRows;
  }

  private void applyRanking(Candidate candidate,
      int ranking,
      Map<String, Integer> previousRankings,
      OffsetDateTime now) {
    BottomDuoStatAggregate agg = candidate.agg();
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
  }

  private static Map<String, List<BottomDuoStatAggregate>> groupStatsByTier(
      List<BottomDuoStatAggregate> currentStats) {
    return currentStats.stream()
        .collect(Collectors.groupingBy(BottomDuoStatAggregate::getTier));
  }

  private static Map<String, Integer> calculateTotalGamesByTier(
      List<BottomDuoStatAggregate> currentStats) {
    return currentStats.stream()
        .collect(Collectors.groupingBy(BottomDuoStatAggregate::getTier,
            Collectors.summingInt(BottomDuoStatAggregate::getGames)));
  }

  private List<BottomDuoStatAggregate> loadCurrentStats(String patchVersion, String tier) {
    if (tier == null) {
      return repository.findByPatchVersion(patchVersion);
    }
    return repository.findByPatchVersionAndTier(patchVersion, tier);
  }

  private Map<String, Integer> loadPreviousRankings(String currentPatch, String tier) {
    String previousPatch = repository.findPreviousPatchVersion(currentPatch);
    if (previousPatch == null) {
      return Map.of();
    }
    List<BottomDuoStatAggregate> previousStats = tier == null
        ? repository.findByPatchVersion(previousPatch)
        : repository.findByPatchVersionAndTier(previousPatch, tier);
    return previousStats.stream()
        .filter(e -> e.getRanking() != null)
        .collect(Collectors.toMap(BottomDuoStatAggregate::duoKey, BottomDuoStatAggregate::getRanking, (left, right) -> left, HashMap::new));
  }

  private double computeRankScore(BottomDuoStatAggregate agg, double pickRate, int tierMaxGames) {
    double winScore = agg.getAdjustedWinRate();
    double pickScore = pickRate;
    double gameScore = tierMaxGames <= 0
        ? 0.0
        : Math.min(1.0, Math.log1p(agg.getGames()) / Math.log1p(tierMaxGames));
    return 0.40 * winScore + 0.20 * pickScore + 0.40 * gameScore;
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

  private record CandidateBuildResult(List<Candidate> eligible, int insufficientRows) {}

  private record Candidate(BottomDuoStatAggregate agg, double pickRate, double rankScore) {}
}
