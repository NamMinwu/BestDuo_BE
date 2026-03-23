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
  public Result execute() {
    String currentPatch = findCurrentPatch();
    if (currentPatch == null) {
      return new Result(null, 0);
    }

    List<BottomDuoStatAggregate> currentStats = loadCurrentStats(currentPatch);
    if (currentStats.isEmpty()) {
      return new Result(currentPatch, 0);
    }

    Map<String, Integer> totalGamesByTier = calculateTotalGamesByTier(currentStats);
    Map<String, Integer> previousRankings = loadPreviousRankings(currentPatch);
    Map<String, List<BottomDuoStatAggregate>> statsByTier = groupStatsByTier(currentStats);

    int updatedRows = applyRankings(statsByTier, totalGamesByTier, previousRankings);

    repository.saveAll(currentStats);
    return new Result(currentPatch, updatedRows);
  }

  private int applyRankings(Map<String, List<BottomDuoStatAggregate>> statsByTier,
      Map<String, Integer> totalGamesByTier,
      Map<String, Integer> previousRankings) {
    int updatedRows = 0;
    OffsetDateTime now = OffsetDateTime.now();

    for (Map.Entry<String, List<BottomDuoStatAggregate>> entry : statsByTier.entrySet()) {
      String tier = entry.getKey();
      int tierTotalGames = totalGamesByTier.getOrDefault(tier, 0);
      CandidateBuildResult candidates = buildEligibleCandidates(entry.getValue(), tierTotalGames, now);
      updatedRows += candidates.insufficientRows();
      updatedRows += applyRankingsForTier(candidates.eligible(), previousRankings, now);
    }

    return updatedRows;
  }

  private CandidateBuildResult buildEligibleCandidates(List<BottomDuoStatAggregate> tierStats,
      int tierTotalGames,
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

      double rankScore = computeRankScore(agg, pickRate);
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

  private String findCurrentPatch() {
    return repository.findLatestPatchVersion();
  }

  private List<BottomDuoStatAggregate> loadCurrentStats(String patchVersion) {
    return repository.findByPatchVersion(patchVersion);
  }

  private Map<String, Integer> loadPreviousRankings(String currentPatch) {
    String previousPatch = repository.findPreviousPatchVersion(currentPatch);
    if (previousPatch == null) {
      return Map.of();
    }
    return repository.findByPatchVersion(previousPatch).stream()
        .filter(e -> e.getRanking() != null)
        .collect(Collectors.toMap(BottomDuoStatAggregate::duoKey, BottomDuoStatAggregate::getRanking, (left, right) -> left, HashMap::new));
  }

  private double computeRankScore(BottomDuoStatAggregate agg, double pickRate) {
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

  private record CandidateBuildResult(List<Candidate> eligible, int insufficientRows) {}

  private record Candidate(BottomDuoStatAggregate agg, double pickRate, double rankScore) {}
}
