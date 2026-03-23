package com.bestduo_BE.refresh.application;

import com.bestduo_BE.refresh.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSummonerMatches {

  private static final int FETCH_COUNT = 50;
  private static final int PRIORITY_REFRESH = 10;

  private final LeagueEntriesRefreshLoader leagueEntriesRefreshLoader;
  private final MatchIdsFinder matchIdsFinder;
  private final MatchQueueEnqueuer matchQueueEnqueuer;
  private final RiotMatchLoader riotMatchLoader;

  private final SummonerRefreshStatusUpdater summonerRefreshStatusUpdater;

  public Result execute(String puuid) {
    return execute(puuid, Tier.ALL_TIERS);
  }

  public Result execute(String puuid, Tier requestedTier) {
    Summoner summoner = summonerRefreshStatusUpdater.findOrCreate(puuid);

    try {
      Tier collectionTier = resolveCollectionTierBySolo(puuid);
      if (collectionTier == null || collectionTier == Tier.ALL_TIERS) {
        summonerRefreshStatusUpdater.syncRefreshCursor(puuid, summoner.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, summoner.getLastMatchStartTime());
      }

      if (requestedTier != null && requestedTier != Tier.ALL_TIERS && requestedTier != collectionTier) {
        summonerRefreshStatusUpdater.syncRefreshCursor(puuid, summoner.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, summoner.getLastMatchStartTime());
      }

      List<String> matchIds = loadMatchIds(puuid, summoner.getLastMatchStartTime());

      if (matchIds == null || matchIds.isEmpty()) {
        summonerRefreshStatusUpdater.syncRefreshCursor(puuid, summoner.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, summoner.getLastMatchStartTime());
      }

      matchQueueEnqueuer.enqueueAllIdempotent(matchIds, collectionTier, PRIORITY_REFRESH);

      Long newCursor = loadNewestMatchStartTimeSec(matchIds.get(0));
      if (newCursor == null) {
        newCursor = summoner.getLastMatchStartTime();
      }

      summonerRefreshStatusUpdater.syncRefreshCursor(puuid, newCursor);

      return new Result(puuid, matchIds.size(), collectionTier, newCursor);

    } catch (Exception e) {
      log.error("Refresh enqueue failed. puuid={}", puuid, e);
      summonerRefreshStatusUpdater.syncRefreshCursor(puuid, summoner.getLastMatchStartTime());
      throw e;
    }
  }

  private List<String> loadMatchIds(String puuid, Long lastMatchStartTimeSecOrNull) {
    if (lastMatchStartTimeSecOrNull == null) {
      return matchIdsFinder.findRecentMatchIds(puuid, FETCH_COUNT);
    }
    return matchIdsFinder.findMatchIdsSince(puuid, lastMatchStartTimeSecOrNull, FETCH_COUNT);
  }

  private Long loadNewestMatchStartTimeSec(String newestMatchId) {
    try {
      RiotMatchDto match = riotMatchLoader.loadMatch(newestMatchId);
      if (match == null || match.info() == null || match.info().gameStartTimestamp() == null) {
        return null;
      }
      return match.info().gameStartTimestamp() / 1000L;
    } catch (Exception e) {
      log.warn("Failed to load newest match for cursor. matchId={}", newestMatchId, e);
      return null;
    }
  }

  private Tier resolveCollectionTierBySolo(String puuid) {
    List<LeagueEntry> entries = leagueEntriesRefreshLoader.loadEntriesByPuuid(puuid);

    LeagueEntry solo = entries.stream()
        .filter(e -> "RANKED_SOLO_5x5".equals(e.queueType()))
        .max(Comparator.comparingLong(e -> e.leaguePoints() == null ? 0L : e.leaguePoints()))
        .orElse(null);

    if (solo == null || solo.tier() == null) return null;

    try {
      Tier riotTier = Tier.valueOf(solo.tier());
      return switch (riotTier) {
        default -> riotTier;
      };
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public record Result(
      String puuid,
      int matchIdsEnqueued,
      Tier collectionTier,
      Long lastMatchStartTimeSec
  ) {}
}
