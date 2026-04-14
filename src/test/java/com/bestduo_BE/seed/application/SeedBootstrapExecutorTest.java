package com.bestduo_BE.seed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.seed.application.port.LeagueEntriesSeedLoader;
import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeedBootstrapExecutorTest {

  @Mock
  private LeagueEntriesSeedLoader leagueEntriesSeedLoader;

  @Mock
  private SummonerSeedRegistry summonerSeedRegistry;

  private SeedBootstrapExecutor useCase;

  private final SeedBootstrapCommand command = new SeedBootstrapCommand(
      "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.MASTER, 1, 1, 0, 0
  );

  @BeforeEach
  void setUp() {
    useCase = new SeedBootstrapExecutor(leagueEntriesSeedLoader, summonerSeedRegistry);
  }

  @Test
  @DisplayName("빈 응답이면 결과 카운터가 모두 0")
  void returnZerosWhenLoaderReturnsEmptyList() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of());

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(command);

    assertThat(result.pagesProcessed()).isZero();
    assertThat(result.entriesFetched()).isZero();
    assertThat(result.summonersSeeded()).isZero();
    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  @DisplayName("엔트리마다 upsertSeeded 호출")
  void upsertSeededCalledForEachEntry() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("p1"), entry("p2")));

    useCase.execute(command);

    verify(summonerSeedRegistry, times(2)).upsertSeeded(any(), any(), any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("기존 registerIfAbsent는 호출되지 않음")
  void registerIfAbsentNeverCalled() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("p1")));

    useCase.execute(command);

    verify(summonerSeedRegistry, never()).registerIfAbsent(any(), any(), any());
  }

  @Test
  @DisplayName("matchIdsFetched, matchIdsEnqueued는 항상 0 (하위 호환)")
  void matchIdsFieldsAlwaysZero() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("p1")));

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(command);

    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  @DisplayName("maxEntries 제한 준수")
  void limitNumberOfEntriesWhenMaxEntriesConfigured() {
    SeedBootstrapCommand limited = new SeedBootstrapCommand(
        "RANKED_SOLO_5x5", "MASTER", "I", Tier.MASTER, 1, 1, 0, 1
    );
    given(leagueEntriesSeedLoader.loadEntries(
        limited.queue(), limited.tier(), limited.division(), 1))
        .willReturn(List.of(entry("p1"), entry("p2")));

    SeedBootstrapExecutor.SeedBootstrapResult result = useCase.execute(limited);

    verify(summonerSeedRegistry, times(1)).upsertSeeded(any(), any(), any());
    assertThat(result.summonersSeeded()).isEqualTo(1);
  }

  @Test
  @DisplayName("엔트리의 tier 문자열로 Tier 파싱")
  void usesTierFromEntry() {
    LeagueEntry grandmasterEntry = new LeagueEntry("gm-puuid", "GRANDMASTER", "RANKED_SOLO_5x5", "I", 100L);
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(grandmasterEntry));

    useCase.execute(command);

    verify(summonerSeedRegistry).upsertSeeded(eq("gm-puuid"), eq(Tier.GRANDMASTER), any());
  }

  @Test
  @DisplayName("tier 문자열이 없으면 seedTier를 사용")
  void fallsBackToSeedTierWhenEntryTierMissing() {
    LeagueEntry noTierEntry = new LeagueEntry("p-x", null, "RANKED_SOLO_5x5", "I", 100L);
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(noTierEntry));

    useCase.execute(command);

    verify(summonerSeedRegistry).upsertSeeded(eq("p-x"), eq(Tier.MASTER), any());
  }

  private LeagueEntry entry(String puuid) {
    return new LeagueEntry(puuid, "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
  }
}
