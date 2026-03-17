package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.LeagueEntriesSeedLoader;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.infra.riot.dto.LeagueList;
import com.bestduo_BE.infra.riot.exception.RiotApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class LeagueEntriesSeedLoaderImpl implements LeagueEntriesSeedLoader {

  private final RestTemplate platformRestTemplate;

  private static final Map<Tier, String> TOP_TIER_ENDPOINTS = Map.of(
      Tier.CHALLENGER, "/lol/league/v4/challengerleagues/by-queue/{queue}",
      Tier.GRANDMASTER, "/lol/league/v4/grandmasterleagues/by-queue/{queue}",
      Tier.MASTER, "/lol/league/v4/masterleagues/by-queue/{queue}"
  );

  public LeagueEntriesSeedLoaderImpl(
      @Qualifier("riotPlatformRestTemplate") RestTemplate platformRestTemplate) {
    this.platformRestTemplate = platformRestTemplate;
  }

  @Override
  public List<LeagueEntry> loadEntries(String queue, String tier, String division, int page) {
    try {
      Tier targetTier = resolveTier(tier);
      if (isTopTier(targetTier)) {
        if (page > 1) {
          return List.of();
        }
        return loadTopTierEntries(queue, targetTier);
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
      log.error(
          "Failed to load league entries. queue={}, tier={}, division={}, page={}",
          queue,
          tier,
          division,
          page,
          e);
      throw new RiotApiException("Failed to load league entries from Riot API", e);
    }
  }

  private boolean isTopTier(Tier tier) {
    return tier != null && TOP_TIER_ENDPOINTS.containsKey(tier);
  }

  private List<LeagueEntry> loadTopTierEntries(String queue, Tier tier) {
    LeagueList leagueList = platformRestTemplate.getForObject(
        TOP_TIER_ENDPOINTS.get(tier),
        LeagueList.class,
        queue
    );
    if (leagueList == null || leagueList.entries() == null) {
      return List.of();
    }
    return leagueList.entries();
  }

  private Tier resolveTier(String tier) {
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
