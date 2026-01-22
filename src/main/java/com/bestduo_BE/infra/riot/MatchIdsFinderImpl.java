package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.MatchIdsFinder;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class MatchIdsFinderImpl implements MatchIdsFinder {
  private final RestTemplate regionalRestTemplate;

  public MatchIdsFinderImpl(
      @Qualifier("riotRegionalRestTemplate") RestTemplate regionalRestTemplate) {
    this.regionalRestTemplate = regionalRestTemplate;
  }

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
      log.error(
          "Failed to load match ids. puuid={}, count={}",
          puuid,
          count,
          e);
      throw new RiotApiException("Failed to load match ids from Riot API", e);
    }
  }
}
