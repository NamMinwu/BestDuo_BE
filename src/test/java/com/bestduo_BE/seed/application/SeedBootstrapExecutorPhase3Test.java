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

/**
 * Phase 3 이후 SeedBootstrapExecutor 동작 검증.
 * matchIds enqueue가 제거되고 upsertSeeded가 추가된 버전.
 */
@ExtendWith(MockitoExtension.class)
class SeedBootstrapExecutorPhase3Test {

  @Mock
  private LeagueEntriesSeedLoader leagueEntriesSeedLoader;

  @Mock
  private SummonerSeedRegistry summonerSeedRegistry;

  private SeedBootstrapExecutor executor;

  private final SeedBootstrapCommand command = new SeedBootstrapCommand(
      "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.DIAMOND, 1, 1, 0, 0
  );

  @BeforeEach
  void setUp() {
    executor = new SeedBootstrapExecutor(leagueEntriesSeedLoader, summonerSeedRegistry);
  }

  @Test
  @DisplayName("빈 응답이면 결과가 모두 0")
  void execute_whenEmpty_returnsZeros() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of());

    SeedBootstrapExecutor.SeedBootstrapResult result = executor.execute(command);

    assertThat(result.pagesProcessed()).isZero();
    assertThat(result.entriesFetched()).isZero();
    assertThat(result.summonersSeeded()).isZero();
  }

  @Test
  @DisplayName("엔트리가 있으면 모든 summoner에 upsertSeeded 호출")
  void execute_withEntries_upsertsAllSummoners() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("puuid-1"), entry("puuid-2")));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertSeeded(eq("puuid-1"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
    verify(summonerSeedRegistry).upsertSeeded(eq("puuid-2"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("신규 summoner뿐 아니라 기존 summoner도 upsertSeeded 호출 (기존 registerIfAbsent 없음)")
  void execute_upsertCalledForAllEntriesRegardlessOfExistence() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("existing-1"), entry("existing-2")));

    executor.execute(command);

    verify(summonerSeedRegistry, times(2)).upsertSeeded(any(), any(), any());
    verify(summonerSeedRegistry, never()).registerIfAbsent(any(), any(), any());
  }

  @Test
  @DisplayName("puuid가 null이거나 blank인 엔트리는 건너뜀")
  void execute_skipsEntriesWithBlankPuuid() {
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry(""), entry("  "), entry("valid-puuid")));

    SeedBootstrapExecutor.SeedBootstrapResult result = executor.execute(command);

    verify(summonerSeedRegistry, times(1)).upsertSeeded(any(), any(), any());
    assertThat(result.summonersSeeded()).isEqualTo(1);
  }

  @Test
  @DisplayName("maxEntries 제한이 적용된다")
  void execute_respectsMaxEntries() {
    SeedBootstrapCommand limited = new SeedBootstrapCommand(
        "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.DIAMOND, 1, 1, 0, 1
    );
    given(leagueEntriesSeedLoader.loadEntries(
        limited.queue(), limited.tier(), limited.division(), 1))
        .willReturn(List.of(entry("p1"), entry("p2"), entry("p3")));

    SeedBootstrapExecutor.SeedBootstrapResult result = executor.execute(limited);

    verify(summonerSeedRegistry, times(1)).upsertSeeded(any(), any(), any());
    assertThat(result.summonersSeeded()).isEqualTo(1);
  }

  @Test
  @DisplayName("엔트리의 tier 문자열이 있으면 해당 tier로 upsert")
  void execute_usesTierFromEntry() {
    LeagueEntry masterEntry = new LeagueEntry("puuid-m", "MASTER", "RANKED_SOLO_5x5", "I", 100L);
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(masterEntry));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertSeeded(eq("puuid-m"), eq(Tier.MASTER), any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("엔트리의 tier 문자열이 없으면 seedTier(fallback) 사용")
  void execute_fallsBackToSeedTierWhenEntryTierMissing() {
    LeagueEntry noTierEntry = new LeagueEntry("puuid-x", null, "RANKED_SOLO_5x5", "I", 100L);
    given(leagueEntriesSeedLoader.loadEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(noTierEntry));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertSeeded(eq("puuid-x"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
  }

  private LeagueEntry entry(String puuid) {
    return new LeagueEntry(puuid, "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
  }
}
