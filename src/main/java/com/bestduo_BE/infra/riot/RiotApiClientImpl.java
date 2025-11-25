package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.RiotApiClient;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class RiotApiClientImpl implements RiotApiClient {

  // 리저널 엔드포인트용 (match-v5 같은 거)
  private final RestTemplate regionalRestTemplate;

  public RiotApiClientImpl(
      @Qualifier("riotPlatformRestTemplate") RestTemplate platformRestTemplate,
      @Qualifier("riotRegionalRestTemplate") RestTemplate regionalRestTemplate) {
    // 플랫폼 엔드포인트용 (소환사, 리그 정보 등)
    this.regionalRestTemplate = regionalRestTemplate;
  }

  @Override
  public List<String> loadMatchIdsByPuuid(String puuid, int count) {
    try {
      String[] ids = regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}",
          String[].class,
          puuid,
          count
      );
      return ids == null ? List.of() : Arrays.asList(ids);
    } catch (RestClientException e) {
      log.error("Failed to load match ids. puuid={}, count={}", puuid, count, e);
      throw new RiotApiException("Failed to load match ids from Riot API", e);
    }
  }

  @Override
  public RiotMatchDto loadMatch(String matchId) {
    try {
      return regionalRestTemplate.getForObject(
          "/lol/match/v5/matches/{matchId}",
          RiotMatchDto.class,
          matchId
      );
    } catch (RestClientException e) {
      log.error("Failed to load match. matchId={}", matchId, e);
      throw new RiotApiException("Failed to load match from Riot API", e);
    }
  }

  private class RiotApiException extends RuntimeException {
    public RiotApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
