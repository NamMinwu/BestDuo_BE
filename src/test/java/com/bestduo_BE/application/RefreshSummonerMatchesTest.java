package com.bestduo_BE.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bestduo_BE.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshSummonerMatchesTest {

  @Mock
  private LeagueEntriesRefreshLoader leagueEntriesRefreshLoader;

  @Mock
  private MatchIdsFinder matchIdsFinder;

  @Mock
  private MatchQueueEnqueuer matchQueueEnqueuer;

  @Mock
  private SummonerRefreshStatusUpdater summonerRefreshStatusUpdater;

  private RefreshSummonerMatches useCase;

  @BeforeEach
  void setUp() {
    useCase = new RefreshSummonerMatches(
        leagueEntriesRefreshLoader,
        matchIdsFinder,
        matchQueueEnqueuer,
        summonerRefreshStatusUpdater
    );
  }

  @Test
  void skipWhenSoloTierIsMissing() {
    Summoner summoner = summoner("p1", 1234L);
    given(summonerRefreshStatusUpdater.findOrCreate("p1")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p1"))
        .willReturn(List.of(new LeagueEntry("p1", "EMERALD", "RANKED_FLEX_SR", "I", 10L)));

    RefreshSummonerMatches.Result result = useCase.execute("p1");

    verify(summonerRefreshStatusUpdater).markRefreshRunning("p1");
    verify(summonerRefreshStatusUpdater).markRefreshDone("p1", 1234L);
    verifyNoInteractions(matchIdsFinder, matchQueueEnqueuer);
    assertThat(result.matchIdsEnqueued()).isZero();
    assertThat(result.collectionTier()).isNull();
  }

  @Test
  void enqueueRecentMatchesWhenTierResolvedAndNoCursor() {
    Summoner summoner = summoner("p-new", null);
    given(summonerRefreshStatusUpdater.findOrCreate("p-new")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-new"))
        .willReturn(List.of(new LeagueEntry("p-new", "EMERALD", "RANKED_SOLO_5x5", "I", 200L)));
    given(matchIdsFinder.findRecentMatchIds("p-new", 50)).willReturn(List.of("m-1", "m-2"));

    RefreshSummonerMatches.Result result = useCase.execute("p-new");

    verify(matchQueueEnqueuer).enqueueAllIdempotent(List.of("m-1", "m-2"), Tier.EMERALD, 10);
    verify(summonerRefreshStatusUpdater).markRefreshDone("p-new", null);
    assertThat(result.matchIdsEnqueued()).isEqualTo(2);
    assertThat(result.collectionTier()).isEqualTo(Tier.EMERALD);
  }

  @Test
  void useFindMatchIdsSinceWhenCursorExists() {
    Summoner summoner = summoner("p-mid", 5678L);
    given(summonerRefreshStatusUpdater.findOrCreate("p-mid")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-mid"))
        .willReturn(List.of(new LeagueEntry("p-mid", "DIAMOND", "RANKED_SOLO_5x5", "I", 30L)));
    given(matchIdsFinder.findMatchIdsSince("p-mid", 5678L, 50)).willReturn(List.of("m-42"));

    RefreshSummonerMatches.Result result = useCase.execute("p-mid");

    verify(matchQueueEnqueuer).enqueueAllIdempotent(List.of("m-42"), Tier.DIAMOND, 10);
    assertThat(result.lastMatchStartTimeSec()).isEqualTo(5678L);
  }

  @Test
  void markErrorAndRethrowWhenEnqueueFails() {
    Summoner summoner = summoner("p-err", null);
    given(summonerRefreshStatusUpdater.findOrCreate("p-err")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-err"))
        .willReturn(List.of(new LeagueEntry("p-err", "MASTER", "RANKED_SOLO_5x5", "I", 300L)));
    given(matchIdsFinder.findRecentMatchIds("p-err", 50)).willReturn(List.of("m-err"));
    willThrow(new IllegalStateException("queue down"))
        .given(matchQueueEnqueuer)
        .enqueueAllIdempotent(List.of("m-err"), Tier.MASTER, 10);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> useCase.execute("p-err"));
    assertThat(ex).hasMessageContaining("queue down");

    verify(summonerRefreshStatusUpdater).markRefreshError("p-err");
  }

  private Summoner summoner(String puuid, Long lastMatchStartTime) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .seedStatus("READY")
        .expandStatus("READY")
        .refreshStatus("READY")
        .lastMatchStartTime(lastMatchStartTime)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
