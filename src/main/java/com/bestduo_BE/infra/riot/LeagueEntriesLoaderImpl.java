package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.LeagueEntriesLoader;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class LeagueEntriesLoaderImpl implements LeagueEntriesLoader {

  private final RestTemplate platformRestTemplate;

  public LeagueEntriesLoaderImpl(
      @Qualifier("riotPlatformRestTemplate") RestTemplate platformRestTemplate) {
    this.platformRestTemplate = platformRestTemplate;
  }

  @Override
  public List<LeagueEntry> loadEntries(String queue, String tier, String division, int page) {
    try {
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
}
