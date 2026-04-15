package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeRiotApiAdapterTest {

  private FakeRiotApiAdapter fake;

  @BeforeEach
  void setUp() {
    fake = new FakeRiotApiAdapter();
  }

  // ── matchIds ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("등록된 matchIds를 findRecentMatchIds로 반환한다")
  void findRecentMatchIds_returnsRegistered() {
    fake.stubMatchIds("puuid-1", List.of("KR_1", "KR_2", "KR_3"));

    List<String> result = fake.findRecentMatchIds("puuid-1", 5);

    assertThat(result).containsExactly("KR_1", "KR_2", "KR_3");
  }

  @Test
  @DisplayName("count 제한이 적용된다")
  void findRecentMatchIds_respectsCount() {
    fake.stubMatchIds("puuid-1", List.of("KR_1", "KR_2", "KR_3"));

    List<String> result = fake.findRecentMatchIds("puuid-1", 2);

    assertThat(result).containsExactly("KR_1", "KR_2");
  }

  @Test
  @DisplayName("등록되지 않은 puuid는 빈 리스트 반환")
  void findRecentMatchIds_unknownPuuid_returnsEmpty() {
    List<String> result = fake.findRecentMatchIds("unknown-puuid", 5);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findMatchIdsSince는 등록된 matchIds를 반환한다")
  void findMatchIdsSince_returnsRegistered() {
    fake.stubMatchIds("puuid-1", List.of("KR_1", "KR_2"));

    List<String> result = fake.findMatchIdsSince("puuid-1", 1700000000L, 10);

    assertThat(result).containsExactly("KR_1", "KR_2");
  }

  @Test
  @DisplayName("findMatchIdsBetween은 등록된 matchIds를 반환한다")
  void findMatchIdsBetween_returnsRegistered() {
    fake.stubMatchIds("puuid-1", List.of("KR_1", "KR_2"));

    List<String> result = fake.findMatchIdsBetween("puuid-1", 1699000000L, 1700000000L, 10);

    assertThat(result).containsExactly("KR_1", "KR_2");
  }

  // ── loadMatch ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("등록된 match를 matchId로 반환한다")
  void loadMatch_returnsRegistered() {
    RiotMatchDto match = new RiotMatchDto(null, null);
    fake.stubMatch("KR_1", match);

    RiotMatchDto result = fake.loadMatch("KR_1");

    assertThat(result).isSameAs(match);
  }

  @Test
  @DisplayName("등록되지 않은 matchId는 null 반환")
  void loadMatch_unknownMatchId_returnsNull() {
    RiotMatchDto result = fake.loadMatch("KR_UNKNOWN");

    assertThat(result).isNull();
  }

  // ── loadLeagueEntries ────────────────────────────────────────────────────

  @Test
  @DisplayName("등록된 league entries를 tier로 반환한다")
  void loadLeagueEntries_returnsRegistered() {
    LeagueEntry entry = new LeagueEntry("puuid-1", "DIAMOND", "RANKED_SOLO_5x5", "I", 100L);
    fake.stubLeagueEntries("DIAMOND", "I", List.of(entry));

    List<LeagueEntry> result = fake.loadLeagueEntries(
        "RANKED_SOLO_5x5", "DIAMOND", "I", 1);

    assertThat(result).containsExactly(entry);
  }

  @Test
  @DisplayName("등록되지 않은 tier/division은 빈 리스트 반환")
  void loadLeagueEntries_unknownTierDivision_returnsEmpty() {
    List<LeagueEntry> result = fake.loadLeagueEntries(
        "RANKED_SOLO_5x5", "PLATINUM", "II", 1);

    assertThat(result).isEmpty();
  }
}
