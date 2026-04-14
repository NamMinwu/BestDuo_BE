package com.bestduo_BE.common.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.LeagueList;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("loadEntries — 페이지네이션 엔드포인트에서 엔트리를 반환한다")
  void loadEntries_returnsEntriesFromPaginatedEndpoint() {
    LeagueEntry entry1 = new LeagueEntry("puuid-1", "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
    LeagueEntry entry2 = new LeagueEntry("puuid-2", "DIAMOND", "RANKED_SOLO_5x5", "I", 80L);
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
        LeagueEntry[].class,
        "RANKED_SOLO_5x5",
        "DIAMOND",
        "I",
        3
    )).willReturn(new LeagueEntry[]{entry1, entry2});

    List<LeagueEntry> result = loader.loadEntries("RANKED_SOLO_5x5", "DIAMOND", "I", 3);

    assertEquals(List.of(entry1, entry2), result);
    then(platformRestTemplate).should().getForObject(
        eq("/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}"),
        eq(LeagueEntry[].class),
        eq("RANKED_SOLO_5x5"),
        eq("DIAMOND"),
        eq("I"),
        eq(3)
    );
  }

  @Test
  @DisplayName("loadEntries — 페이지네이션 API가 null을 반환하면 빈 리스트를 반환한다")
  void loadEntries_returnsEmptyListWhenPaginatedApiReturnsNull() {
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/entries/{queue}/{tier}/{division}?page={page}",
        LeagueEntry[].class,
        "RANKED_FLEX_SR",
        "PLATINUM",
        "II",
        1
    )).willReturn(null);

    List<LeagueEntry> result = loader.loadEntries("RANKED_FLEX_SR", "PLATINUM", "II", 1);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("loadEntries — REST 예외를 RiotApiException으로 감싼다")
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

  @Test
  @DisplayName("loadEntries — 최상위 티어는 전용 엔드포인트에서 엔트리를 가져온다 (CHALLENGER)")
  void loadEntries_fetchesTopTierEntriesFromDedicatedEndpoint() {
    LeagueEntry entry1 = new LeagueEntry("puuid-1", "CHALLENGER", "RANKED_SOLO_5x5", "I", 500L);
    LeagueEntry entry2 = new LeagueEntry("puuid-2", "CHALLENGER", "RANKED_SOLO_5x5", "I", 450L);
    LeagueList leagueList = new LeagueList(List.of(entry1, entry2));
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/challengerleagues/by-queue/{queue}",
        LeagueList.class,
        "RANKED_SOLO_5x5"
    )).willReturn(leagueList);

    List<LeagueEntry> result = loader.loadEntries("RANKED_SOLO_5x5", "CHALLENGER", "I", 1);

    assertEquals(List.of(entry1, entry2), result);
    then(platformRestTemplate).should().getForObject(
        eq("/lol/league/v4/challengerleagues/by-queue/{queue}"),
        eq(LeagueList.class),
        eq("RANKED_SOLO_5x5")
    );
  }

  @Test
  @DisplayName("loadEntries — MASTER 티어 전용 엔드포인트에서 엔트리를 가져온다")
  void loadEntries_fetchesMasterTierEntriesFromDedicatedEndpoint() {
    LeagueEntry entry1 = new LeagueEntry("puuid-3", "MASTER", "RANKED_SOLO_5x5", "I", 250L);
    LeagueEntry entry2 = new LeagueEntry("puuid-4", "MASTER", "RANKED_SOLO_5x5", "I", 200L);
    LeagueList leagueList = new LeagueList(List.of(entry1, entry2));
    given(platformRestTemplate.getForObject(
        "/lol/league/v4/masterleagues/by-queue/{queue}",
        LeagueList.class,
        "RANKED_SOLO_5x5"
    )).willReturn(leagueList);

    List<LeagueEntry> result = loader.loadEntries("RANKED_SOLO_5x5", "MASTER", "I", 1);

    assertEquals(List.of(entry1, entry2), result);
    then(platformRestTemplate).should().getForObject(
        eq("/lol/league/v4/masterleagues/by-queue/{queue}"),
        eq(LeagueList.class),
        eq("RANKED_SOLO_5x5")
    );
  }

  @Test
  @DisplayName("loadEntries — 최상위 티어에서 page가 1보다 크면 빈 리스트를 반환한다")
  void loadEntries_returnsEmptyListForTopTierWhenPageGreaterThanOne() {
    List<LeagueEntry> result = loader.loadEntries("RANKED_SOLO_5x5", "MASTER", "I", 2);

    assertTrue(result.isEmpty());
    verifyNoInteractions(platformRestTemplate);
  }
}
