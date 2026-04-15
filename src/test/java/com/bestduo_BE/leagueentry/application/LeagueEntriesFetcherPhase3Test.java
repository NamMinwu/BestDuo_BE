package com.bestduo_BE.leagueentry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.LeagueEntriesFetchCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher.LeagueEntriesFetchResult;
import com.bestduo_BE.leagueentry.application.port.SummonerSeedRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueEntriesFetcherPhase3Test {

  @Mock
  private RiotApiPort riotApiPort;

  @Mock
  private SummonerSeedRegistry summonerSeedRegistry;

  private LeagueEntriesFetcher executor;

  private final LeagueEntriesFetchCommand command = new LeagueEntriesFetchCommand(
      "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.DIAMOND, 1, 0
  );

  @BeforeEach
  void setUp() {
    executor = new LeagueEntriesFetcher(riotApiPort, summonerSeedRegistry);
  }

  @Test
  @DisplayName("빈 응답이면 결과가 모두 0")
  void execute_whenEmpty_returnsZeros() {
    given(riotApiPort.loadLeagueEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of());

    LeagueEntriesFetchResult result = executor.execute(command);

    assertThat(result.pagesProcessed()).isZero();
    assertThat(result.entriesFetched()).isZero();
    assertThat(result.summonersSeeded()).isZero();
  }

  @Test
  @DisplayName("엔트리가 있으면 모든 summoner에 upsertLeagueEntry 호출")
  void execute_withEntries_upsertsAllSummoners() {
    given(riotApiPort.loadLeagueEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry("puuid-1"), entry("puuid-2")));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertLeagueEntry(eq("puuid-1"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
    verify(summonerSeedRegistry).upsertLeagueEntry(eq("puuid-2"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("puuid가 null이거나 blank인 엔트리는 건너뜀")
  void execute_skipsEntriesWithBlankPuuid() {
    given(riotApiPort.loadLeagueEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(entry(""), entry("  "), entry("valid-puuid")));

    LeagueEntriesFetchResult result = executor.execute(command);

    verify(summonerSeedRegistry, times(1)).upsertLeagueEntry(any(), any(), any());
    assertThat(result.summonersSeeded()).isEqualTo(1);
  }

  @Test
  @DisplayName("maxEntries 제한이 적용된다")
  void execute_respectsMaxEntries() {
    LeagueEntriesFetchCommand limited = new LeagueEntriesFetchCommand(
        "RANKED_SOLO_5x5", "DIAMOND", "I", Tier.DIAMOND, 1, 1
    );
    given(riotApiPort.loadLeagueEntries(
        limited.queue(), limited.tier(), limited.division(), 1))
        .willReturn(List.of(entry("p1"), entry("p2"), entry("p3")));

    LeagueEntriesFetchResult result = executor.execute(limited);

    verify(summonerSeedRegistry, times(1)).upsertLeagueEntry(any(), any(), any());
    assertThat(result.summonersSeeded()).isEqualTo(1);
  }

  @Test
  @DisplayName("엔트리의 tier 문자열이 있으면 해당 tier로 upsert")
  void execute_usesTierFromEntry() {
    LeagueEntry masterEntry = new LeagueEntry("puuid-m", "MASTER", "RANKED_SOLO_5x5", "I", 100L);
    given(riotApiPort.loadLeagueEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(masterEntry));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertLeagueEntry(eq("puuid-m"), eq(Tier.MASTER), any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("엔트리의 tier 문자열이 없으면 seedTier(fallback) 사용")
  void execute_fallsBackToSeedTierWhenEntryTierMissing() {
    LeagueEntry noTierEntry = new LeagueEntry("puuid-x", null, "RANKED_SOLO_5x5", "I", 100L);
    given(riotApiPort.loadLeagueEntries(
        command.queue(), command.tier(), command.division(), 1))
        .willReturn(List.of(noTierEntry));

    executor.execute(command);

    verify(summonerSeedRegistry).upsertLeagueEntry(eq("puuid-x"), eq(Tier.DIAMOND), any(OffsetDateTime.class));
  }

  private LeagueEntry entry(String puuid) {
    return new LeagueEntry(puuid, "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
  }
}
