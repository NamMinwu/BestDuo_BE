package com.bestduo_BE.infra.riot;

import com.bestduo_BE.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.infra.riot.exception.RiotApiException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class LeagueEntriesRefreshLoaderImpl implements LeagueEntriesRefreshLoader {

  private final RestTemplate platformRestTemplate;

  public LeagueEntriesRefreshLoaderImpl(
      @Qualifier("riotPlatformRestTemplate") RestTemplate platformRestTemplate
  ) {
    this.platformRestTemplate = platformRestTemplate;
  }

  @Override
  public List<LeagueEntry> loadEntriesByPuuid(String puuid) {
    try {
      // Riot 응답 DTO를 만들어 파싱하는 게 정석이지만,
      // 여기선 필요한 필드만 매핑한다고 가정
      LeagueEntry[] entries = platformRestTemplate.getForObject(
          "/lol/league/v4/entries/by-puuid/{puuid}",
          LeagueEntry[].class,
          puuid
      );
      return entries == null ? List.of() : Arrays.asList(entries);

    } catch (RestClientException e) {
      log.error("Failed to load league entries by puuid. puuid={}", puuid, e);
      throw new RiotApiException("Failed to load league entries by puuid", e);
    }
  }

}
