package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.RiotMatchLoader;

import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class RiotMatchLoaderImpl implements RiotMatchLoader {

  // 리저널 엔드포인트용 (match-v5 같은 거)
  private final RestTemplate regionalRestTemplate;

  public RiotMatchLoaderImpl(
      @Qualifier("riotRegionalRestTemplate") RestTemplate regionalRestTemplate) {
    this.regionalRestTemplate = regionalRestTemplate;
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

}
