package com.bestduo_BE.seed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.seed.application.port.LeagueEntriesSeedLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeedBootstrapExecutorTest {

  @Mock
  private LeagueEntriesSeedLoader leagueEntriesSeedLoader;

  @Mock
  private MatchIdsFinder matchIdsFinder;

  @Mock
  private MatchQueueEnqueuer matchQueueEnqueuer;

  @Mock
  private SummonerSeedRegistry summonerSeedRegistry;

  private SeedBootstrapExecutor useCase;

  private final SeedBootstrapCommand command = new SeedBootstrapCommand(
      "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.MASTER, 1, 1, 2, 0
  );

  @BeforeEach
  void setUp() {
    useCase = new SeedBootstrapExecutor(
        leagueEntriesSeedLoader,
        matchIdsFinder,
        matchQueueEnqueuer,
        summonerSeedRegistry
    );
  }

  @Test
  void returnZerosWhenLoaderReturnsEmptyList() {
    givenEntries(List.of());

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(command);

    assertThat(result.pagesProcessed()).isZero();
    assertThat(result.entriesFetched()).isZero();
    assertThat(result.puuidRegistered()).isZero();
    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  void registerSeedsAndEnqueueMatchIdsWhenFirstSeen() {
    List<LeagueEntry> entries = List.of(entry("new-1"), entry("existing"));
    givenEntries(entries);
    given(summonerSeedRegistry.registerIfAbsent(eq("new-1"), any(Tier.class), any(OffsetDateTime.class))).willReturn(true);
    given(summonerSeedRegistry.registerIfAbsent(eq("existing"), any(Tier.class), any(OffsetDateTime.class))).willReturn(false);
    given(matchIdsFinder.findRecentMatchIds("new-1", command.matchesPerPuuid()))
        .willReturn(List.of("m-1", "m-2"));

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(command);

    verify(matchQueueEnqueuer).enqueueAllIdempotent(List.of("m-1", "m-2"), Tier.MASTER, 50);
    verify(matchIdsFinder, never()).findRecentMatchIds(eq("existing"), anyInt());

    assertThat(result.pagesProcessed()).isEqualTo(1);
    assertThat(result.entriesFetched()).isEqualTo(2);
    assertThat(result.puuidRegistered()).isEqualTo(1);
    assertThat(result.matchIdsFetched()).isEqualTo(2);
    assertThat(result.matchIdsEnqueued()).isEqualTo(2);
  }

  @Test
  void markSeedErrorWhenMatchLookupFails() {
    givenEntries(List.of(entry("p-error")));
    given(summonerSeedRegistry.registerIfAbsent(eq("p-error"), any(Tier.class), any(OffsetDateTime.class))).willReturn(true);
    given(matchIdsFinder.findRecentMatchIds("p-error", command.matchesPerPuuid()))
        .willThrow(new IllegalStateException("boom"));

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(command);

    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  void limitNumberOfEntriesWhenMaxEntriesConfigured() {
    SeedBootstrapCommand limited = new SeedBootstrapCommand(
        "RANKED_SOLO_5x5", "MASTER", "I", Tier.MASTER, 1, 1, 2, 1
    );
    List<LeagueEntry> entries = List.of(entry("new-1"), entry("new-2"));
    given(leagueEntriesSeedLoader.loadEntries(
        limited.queue(), limited.tier(), limited.division(), 1
    )).willReturn(entries);
    given(summonerSeedRegistry.registerIfAbsent(eq("new-1"), any(Tier.class), any(OffsetDateTime.class))).willReturn(true);
    given(matchIdsFinder.findRecentMatchIds("new-1", limited.matchesPerPuuid()))
        .willReturn(List.of("m-1"));

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(limited);

    verify(summonerSeedRegistry).registerIfAbsent(eq("new-1"), any(Tier.class), any(OffsetDateTime.class));
    verify(summonerSeedRegistry, never()).registerIfAbsent(eq("new-2"), any(Tier.class), any(OffsetDateTime.class));
    assertThat(result.entriesFetched()).isEqualTo(1);
    assertThat(result.puuidRegistered()).isEqualTo(1);
  }

  private void givenEntries(List<LeagueEntry> entries) {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1
    )).willReturn(entries);
  }

  private LeagueEntry entry(String puuid) {
    return new LeagueEntry(puuid, "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
  }
}
