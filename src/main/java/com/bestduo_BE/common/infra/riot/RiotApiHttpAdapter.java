package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.LeagueList;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Riot API HTTP 어댑터 — {@link RiotApiPort} 구현체.
 *
 * <p>기존에 분산되어 있던 세 구현체(MatchIdsFinderImpl, RiotMatchLoaderImpl,
 * LeagueEntriesSeedLoaderImpl)를 단일 어댑터로 통합한다.
 */
@Component
@Slf4j
public class RiotApiHttpAdapter implements RiotApiPort {

  private static final Map<Tier, String> APEX_TIER_ENDPOINTS = Map.of(
      Tier.CHALLENGER, "/lol/league/v4/challengerleagues/by-queue/{queue}",
      Tier.GRANDMASTER, "/lol/league/v4/grandmasterleagues/by-queue/{queue}",
      Tier.MASTER, "/lol/league/v4/masterleagues/by-queue/{queue}"
  );

  private final RestTemplate regionalRestTemplate;
  private final RestTemplate platformRestTemplate;

  public RiotApiHttpAdapter(
      @Qualifier("riotRegionalRestTemplate") RestTemplate regionalRestTemplate,
      @Qualifier("riotPlatformRestTemplate") RestTemplate platformRestTemplate) {
    this.regionalRestTemplate = regionalRestTemplate;
    this.platformRestTemplate = platformRestTemplate;
  }

  // ── Match IDs ──────────────────────────────────────────────────────────

  @Override
  public List<String> findRecentMatchIds(String puuid, int count) {
    try {
      String[] matchIds = regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}",
          String[].class,
          puuid,
          count
      );
      return matchIds == null ? List.of() : Arrays.asList(matchIds);
    } catch (RestClientException e) {
      log.error("matchIds 조회 실패. puuid={}, count={}", puuid, count, e);
      throw new RiotApiException("Failed to load match ids from Riot API", e);
    }
  }

  @Override
  public List<String> findMatchIdsSince(String puuid, long startTimeEpochSeconds, int count) {
    try {
      String[] matchIds = regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&count={count}",
          String[].class,
          puuid,
          startTimeEpochSeconds,
          count
      );
      return matchIds == null ? List.of() : Arrays.asList(matchIds);
    } catch (RestClientException e) {
      log.error("matchIds(since) 조회 실패. puuid={}, startTime={}, count={}",
          puuid, startTimeEpochSeconds, count, e);
      throw new RiotApiException("Failed to load match ids since startTime from Riot API", e);
    }
  }

  @Override
  public List<String> findMatchIdsBetween(
      String puuid, long startTimeEpochSeconds, long endTimeEpochSeconds, int count) {
    try {
      String[] matchIds = regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&endTime={endTime}&count={count}",
          String[].class,
          puuid,
          startTimeEpochSeconds,
          endTimeEpochSeconds,
          count
      );
      return matchIds == null ? List.of() : Arrays.asList(matchIds);
    } catch (RestClientException e) {
      log.error("matchIds(between) 조회 실패. puuid={}, startTime={}, endTime={}, count={}",
          puuid, startTimeEpochSeconds, endTimeEpochSeconds, count, e);
      throw new RiotApiException("Failed to load match ids between timerange from Riot API", e);
    }
  }

  // ── Match Detail ───────────────────────────────────────────────────────

  @Override
  public RiotMatchDto loadMatch(String matchId) {
    try {
      return regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/{matchId}",
          RiotMatchDto.class,
          matchId
      );
    } catch (RestClientException e) {
      log.error("match 조회 실패. matchId={}", matchId, e);
      throw new RiotApiException("Failed to load match from Riot API", e);
    }
  }

  // ── League Entries ─────────────────────────────────────────────────────

  @Override
  public List<LeagueEntry> loadLeagueEntries(
      String queue, String tier, String division, int page) {
    try {
      Tier resolvedTier = resolveTier(tier);
      if (isApexTier(resolvedTier)) {
        return page > 1 ? List.of() : loadApexTierEntries(queue, resolvedTier);
      }

      LeagueEntry[] entries = platformRestTemplate.getForObject(
          "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
          LeagueEntry[].class,
          queue,
          tier,
          division,
          page
      );
      return entries == null ? List.of() : Arrays.asList(entries);
    } catch (RestClientException e) {
      log.error("league entries 조회 실패. queue={}, tier={}, division={}, page={}",
          queue, tier, division, page, e);
      throw new RiotApiException("Failed to load league entries from Riot API", e);
    }
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private boolean isApexTier(Tier tier) {
    return tier != null && APEX_TIER_ENDPOINTS.containsKey(tier);
  }

  private List<LeagueEntry> loadApexTierEntries(String queue, Tier tier) {
    LeagueList leagueList = platformRestTemplate.getForObject(
        APEX_TIER_ENDPOINTS.get(tier),
        LeagueList.class,
        queue
    );
    if (leagueList == null || leagueList.entries() == null) {
      return List.of();
    }
    return leagueList.entries();
  }

  private static Tier resolveTier(String tier) {
    if (tier == null || tier.isBlank()) {
      return null;
    }
    try {
      return Tier.valueOf(tier.toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
