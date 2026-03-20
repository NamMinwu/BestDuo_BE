package com.bestduo_BE.common.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.riot.LeagueEntriesRefreshLoaderImpl;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LeagueEntriesRefreshLoaderImplTest {

  @Mock
  private RestTemplate platformRestTemplate;

  private LeagueEntriesRefreshLoaderImpl loader;

  @BeforeEach
  void setUp() {
    loader = new LeagueEntriesRefreshLoaderImpl(platformRestTemplate);
  }

  @Test
  void loadEntriesByPuuid_returnsEntriesFromPlatformEndpoint() {
    LeagueEntry leagueEntry = new LeagueEntry("puuid-1", "EMERALD", "RANKED_SOLO_5x5", "I", 123L);
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/by-puuid/{puuid}",
        LeagueEntry[].class,
        "puuid-1"
    )).willReturn(new LeagueEntry[]{leagueEntry});

    List<LeagueEntry> entries = loader.loadEntriesByPuuid("puuid-1");

    assertEquals(List.of(leagueEntry), entries);
    then(platformRestTemplate).should().getForObject(
        eq("/lol/league/v4/entries/by-puuid/{puuid}"),
        eq(LeagueEntry[].class),
        eq("puuid-1")
    );
  }

  @Test
  void loadEntriesByPuuid_returnsEmptyListWhenApiReturnsNull() {
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/by-puuid/{puuid}",
        LeagueEntry[].class,
        "puuid-2"
    )).willReturn(null);

    List<LeagueEntry> entries = loader.loadEntriesByPuuid("puuid-2");

    assertTrue(entries.isEmpty());
  }

  @Test
  void loadEntriesByPuuid_wrapsRestExceptionsInRiotApiException() {
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/by-puuid/{puuid}",
        LeagueEntry[].class,
        "puuid-3"
    )).willThrow(new RestClientException("rate limited"));

    assertThrows(RiotApiException.class,
        () -> loader.loadEntriesByPuuid("puuid-3"));
  }
}

