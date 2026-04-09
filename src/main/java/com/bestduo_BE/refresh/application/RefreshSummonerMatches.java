package com.bestduo_BE.refresh.application;

import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.refresh.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
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
  private final SummonerRefreshStatusUpdater summonerRefreshStatusUpdater;
  private final WorkItemProperties workItemProperties;

  public Result execute(String puuid) {
    return execute(puuid, Tier.ALL_TIERS);
  }

  public Result execute(String puuid, Tier requestedTier) {
    Summoner summoner = summonerRefreshStatusUpdater.findOrCreate(puuid);

    try {
      // league-v4 호출 or 캐시 사용 (tierObservedAt이 TTL 이내면 API 스킵)
      Tier collectionTier = resolveTier(puuid, summoner);

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

      long newCursor = Instant.now().getEpochSecond();
      summonerRefreshStatusUpdater.syncRefreshCursor(puuid, newCursor);

      return new Result(puuid, matchIds.size(), collectionTier, newCursor);

    } catch (Exception e) {
      log.error("Refresh enqueue failed. puuid={}", puuid, e);
      summonerRefreshStatusUpdater.syncRefreshCursor(puuid, summoner.getLastMatchStartTime());
      throw e;
    }
  }

  /**
   * 소환사의 실제 tier를 반환한다. tierObservedAt이 TTL 이내면 league-v4 호출을 생략하고
   * 캐시된 lastKnownTier를 재사용한다.
   *
   * <p>캐시 히트: league-v4 API 호출 없음, DB 갱신 없음 (재검증이 아니므로)
   * <p>캐시 미스: league-v4 호출 후 유효한 tier이면 DB에 갱신
   */
  private Tier resolveTier(String puuid, Summoner summoner) {
    int ttlHours = workItemProperties.getThreshold().getTierCacheTtlHours();
    if (summoner.getTierObservedAt() != null
        && summoner.getTierObservedAt().isAfter(OffsetDateTime.now().minusHours(ttlHours))) {
      log.debug("Tier cache hit. puuid={} tier={} observedAt={}",
          puuid, summoner.getLastKnownTier(), summoner.getTierObservedAt());
      return summoner.getLastKnownTier();
    }

    // 캐시 만료 또는 미검증 → league-v4 호출
    Tier resolved = resolveCollectionTierBySolo(puuid);
    if (resolved != null && resolved != Tier.ALL_TIERS) {
      summonerRefreshStatusUpdater.syncResolvedTier(puuid, resolved, OffsetDateTime.now());
    }
    return resolved;
  }

  private List<String> loadMatchIds(String puuid, Long lastMatchStartTimeSecOrNull) {
    if (lastMatchStartTimeSecOrNull == null) {
      return matchIdsFinder.findRecentMatchIds(puuid, FETCH_COUNT);
    }
    return matchIdsFinder.findMatchIdsSince(puuid, lastMatchStartTimeSecOrNull, FETCH_COUNT);
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
