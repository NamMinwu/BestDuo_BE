package com.bestduo_BE.refresh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bestduo_BE.refresh.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.common.application.port.SummonerRefreshCursorTracker;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
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

  @Mock
  private SummonerRefreshCursorTracker summonerRefreshCursorTracker;

  private RefreshSummonerMatches useCase;

  @BeforeEach
  void setUp() {
    useCase = new RefreshSummonerMatches(
        leagueEntriesRefreshLoader,
        matchIdsFinder,
        matchQueueEnqueuer,
        summonerRefreshCursorTracker,
        summonerRefreshStatusUpdater
    );
  }

  @Test
  void skipWhenSoloTierIsMissing() {
    Summoner summoner = summoner("p1", 1234L);
    given(summonerRefreshStatusUpdater.findOrCreate("p1")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p1"))
        .willReturn(List.of(new LeagueEntry("p1", "EMERALD", "RANKED_FLEX_SR", "I", 10L)));

    RefreshSummonerMatches.Result result = useCase.execute("p1", Tier.GOLD);

    verify(summonerRefreshStatusUpdater).syncRefreshCursor("p1", 1234L);
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
    given(summonerRefreshCursorTracker.hasPendingMatches("p-new")).willReturn(false);
    given(matchIdsFinder.findRecentMatchIds("p-new", 50)).willReturn(List.of("m-1", "m-2"));
    given(summonerRefreshCursorTracker.registerRefreshBatch("p-new", List.of("m-1", "m-2"), null))
        .willReturn(12L);

    RefreshSummonerMatches.Result result = useCase.execute("p-new", Tier.EMERALD);

    verify(matchQueueEnqueuer).enqueueAllIdempotent(List.of("m-1", "m-2"), Tier.EMERALD, 10);
    verify(summonerRefreshStatusUpdater).syncRefreshCursor("p-new", 12L);
    assertThat(result.matchIdsEnqueued()).isEqualTo(2);
    assertThat(result.collectionTier()).isEqualTo(Tier.EMERALD);
    assertThat(result.lastMatchStartTimeSec()).isEqualTo(12L);
  }

  @Test
  void useFindMatchIdsSinceWhenCursorExists() {
    Summoner summoner = summoner("p-mid", 5678L);
    given(summonerRefreshStatusUpdater.findOrCreate("p-mid")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-mid"))
        .willReturn(List.of(new LeagueEntry("p-mid", "DIAMOND", "RANKED_SOLO_5x5", "I", 30L)));
    given(summonerRefreshCursorTracker.hasPendingMatches("p-mid")).willReturn(false);
    given(matchIdsFinder.findMatchIdsSince("p-mid", 5678L, 50)).willReturn(List.of("m-42"));
    given(summonerRefreshCursorTracker.registerRefreshBatch("p-mid", List.of("m-42"), 5678L))
        .willReturn(6789L);

    RefreshSummonerMatches.Result result = useCase.execute("p-mid", Tier.DIAMOND);

    verify(matchQueueEnqueuer).enqueueAllIdempotent(List.of("m-42"), Tier.DIAMOND, 10);
    assertThat(result.lastMatchStartTimeSec()).isEqualTo(6789L);
  }

  @Test
  void markErrorAndRethrowWhenEnqueueFails() {
    Summoner summoner = summoner("p-err", null);
    given(summonerRefreshStatusUpdater.findOrCreate("p-err")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-err"))
        .willReturn(List.of(new LeagueEntry("p-err", "MASTER", "RANKED_SOLO_5x5", "I", 300L)));
    given(summonerRefreshCursorTracker.hasPendingMatches("p-err")).willReturn(false);
    given(matchIdsFinder.findRecentMatchIds("p-err", 50)).willReturn(List.of("m-err"));
    willThrow(new IllegalStateException("queue down"))
        .given(matchQueueEnqueuer)
        .enqueueAllIdempotent(List.of("m-err"), Tier.MASTER, 10);

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> useCase.execute("p-err", Tier.MASTER));
    assertThat(ex).hasMessageContaining("queue down");

    verify(summonerRefreshStatusUpdater).syncRefreshCursor("p-err", null);
  }

  @Test
  void skipWhenResolvedTierDoesNotMatchRequestedTier() {
    Summoner summoner = summoner("p-miss", 777L);
    given(summonerRefreshStatusUpdater.findOrCreate("p-miss")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-miss"))
        .willReturn(List.of(new LeagueEntry("p-miss", "EMERALD", "RANKED_SOLO_5x5", "I", 200L)));

    RefreshSummonerMatches.Result result = useCase.execute("p-miss", Tier.GOLD);

    verify(summonerRefreshStatusUpdater).syncRefreshCursor("p-miss", 777L);
    verifyNoInteractions(matchIdsFinder, matchQueueEnqueuer);
    assertThat(result.matchIdsEnqueued()).isZero();
    assertThat(result.collectionTier()).isEqualTo(Tier.EMERALD);
  }

  @Test
  void skipMatchIdLookupWhilePendingRefreshFrontierExists() {
    Summoner summoner = summoner("p-pending", 555L);
    given(summonerRefreshStatusUpdater.findOrCreate("p-pending")).willReturn(summoner);
    given(leagueEntriesRefreshLoader.loadEntriesByPuuid("p-pending"))
        .willReturn(List.of(new LeagueEntry("p-pending", "EMERALD", "RANKED_SOLO_5x5", "I", 200L)));
    given(summonerRefreshCursorTracker.hasPendingMatches("p-pending")).willReturn(true);

    RefreshSummonerMatches.Result result = useCase.execute("p-pending", Tier.EMERALD);

    verify(summonerRefreshStatusUpdater).syncRefreshCursor("p-pending", 555L);
    verifyNoInteractions(matchIdsFinder, matchQueueEnqueuer);
    assertThat(result.matchIdsEnqueued()).isZero();
    assertThat(result.lastMatchStartTimeSec()).isEqualTo(555L);
  }

  private Summoner summoner(String puuid, Long lastMatchStartTime) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .lastMatchStartTime(lastMatchStartTime)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
