package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 인메모리 Riot API 어댑터.
 *
 * <p>HTTP 호출 없이 미리 등록한 데이터를 반환하므로
 * 통합 테스트에서 Mockito 없이 실제 객체 그래프를 구성할 수 있다.
 *
 * <pre>
 * FakeRiotApiAdapter fake = new FakeRiotApiAdapter();
 * fake.stubMatchIds("puuid-1", List.of("KR_1", "KR_2"));
 * fake.stubMatch("KR_1", someMatchDto);
 * </pre>
 */
public class FakeRiotApiAdapter implements RiotApiPort {

  private final Map<String, List<String>> matchIdsStore = new HashMap<>();
  private final Map<String, RiotMatchDto> matchStore = new HashMap<>();
  private final Map<String, List<LeagueEntry>> leagueEntriesStore = new HashMap<>();

  // ── Stub registration ──────────────────────────────────────────────────

  /** puuid에 대한 matchId 목록을 등록한다. */
  public void stubMatchIds(String puuid, List<String> matchIds) {
    matchIdsStore.put(puuid, List.copyOf(matchIds));
  }

  /** matchId에 대한 match 상세 정보를 등록한다. */
  public void stubMatch(String matchId, RiotMatchDto match) {
    matchStore.put(matchId, match);
  }

  /**
   * tier + division 조합에 대한 league entries를 등록한다.
   *
   * @param tier     티어 문자열 (예: "DIAMOND")
   * @param division 디비전 문자열 (예: "I")
   */
  public void stubLeagueEntries(String tier, String division, List<LeagueEntry> entries) {
    leagueEntriesStore.put(leagueKey(tier, division), List.copyOf(entries));
  }

  // ── RiotApiPort ─────────────────────────────────────────────────────────

  @Override
  public List<String> findRecentMatchIds(String puuid, int count) {
    List<String> all = matchIdsStore.getOrDefault(puuid, List.of());
    return all.size() <= count ? all : all.subList(0, count);
  }

  @Override
  public List<String> findMatchIdsSince(String puuid, long startTimeEpochSeconds, int count) {
    return findRecentMatchIds(puuid, count);
  }

  @Override
  public List<String> findMatchIdsBetween(
      String puuid, long startTimeEpochSeconds, long endTimeEpochSeconds, int count) {
    return findRecentMatchIds(puuid, count);
  }

  @Override
  public RiotMatchDto loadMatch(String matchId) {
    return matchStore.get(matchId);
  }

  @Override
  public List<LeagueEntry> loadLeagueEntries(
      String queue, String tier, String division, int page) {
    return leagueEntriesStore.getOrDefault(leagueKey(tier, division), List.of());
  }

  // ── helper ──────────────────────────────────────────────────────────────

  private static String leagueKey(String tier, String division) {
    return tier + ":" + division;
  }
}
