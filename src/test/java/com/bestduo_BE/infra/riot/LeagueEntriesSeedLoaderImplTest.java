package com.bestduo_BE.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LeagueEntriesSeedLoaderImplTest {

  @Mock
  private RestTemplate platformRestTemplate;

  private LeagueEntriesSeedLoaderImpl loader;

  @BeforeEach
  void setUp() {
    loader = new LeagueEntriesSeedLoaderImpl(platformRestTemplate);
  }

  @Test
  void loadEntries_returnsEntriesFromPlatformEndpoint() {
    LeagueEntry entry1 = new LeagueEntry("puuid-1", "MASTER", "RANKED_SOLO_5x5", "I", 100L);
    LeagueEntry entry2 = new LeagueEntry("puuid-2", "MASTER", "RANKED_SOLO_5x5", "I", 80L);
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
        LeagueEntry[].class,
        "RANKED_SOLO_5x5",
        "MASTER",
        "I",
        3
    )).willReturn(new LeagueEntry[]{entry1, entry2});

    List<LeagueEntry> result = loader.loadEntries("RANKED_SOLO_5x5", "MASTER", "I", 3);

    assertEquals(List.of(entry1, entry2), result);
    then(platformRestTemplate).should().getForObject(
        eq("/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}"),
        eq(LeagueEntry[].class),
        eq("RANKED_SOLO_5x5"),
        eq("MASTER"),
        eq("I"),
        eq(3)
    );
  }

  @Test
  void loadEntries_returnsEmptyListWhenApiReturnsNull() {
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
        LeagueEntry[].class,
        "RANKED_FLEX_SR",
        "CHALLENGER",
        "II",
        1
    )).willReturn(null);

    List<LeagueEntry> result = loader.loadEntries("RANKED_FLEX_SR", "CHALLENGER", "II", 1);

    assertTrue(result.isEmpty());
  }

  @Test
  void loadEntries_wrapsRestExceptionsInRiotApiException() {
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
        LeagueEntry[].class,
        "RANKED_SOLO_5x5",
        "DIAMOND",
        "III",
        2
    )).willThrow(new RestClientException("boom"));

    assertThrows(RiotApiException.class,
        () -> loader.loadEntries("RANKED_SOLO_5x5", "DIAMOND", "III", 2));
  }
}
