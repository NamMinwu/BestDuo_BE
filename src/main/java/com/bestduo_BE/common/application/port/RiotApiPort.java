package com.bestduo_BE.common.application.port;

import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;

/**
 * Riot API 호출 경계를 추상화하는 포트 인터페이스 (경량 헥사고날 Phase 5).
 *
 * <p>기존에 분산되어 있던 세 포트를 통합한다:
 * <ul>
 *   <li>{@code MatchIdsFinder} — match ID 조회</li>
 *   <li>{@code RiotMatchLoader} — match 상세 조회</li>
 *   <li>{@code LeagueEntriesSeedLoader} — 리그 엔트리 조회</li>
 * </ul>
 *
 * <p>운영 환경: {@code RiotApiHttpAdapter} (RestTemplate 기반 HTTP 구현체)
 * <p>테스트 환경: {@code FakeRiotApiAdapter} (인메모리 Stub 구현체)
 */
public interface RiotApiPort {

  // ── Match IDs ──────────────────────────────────────────────────────────

  /** 최근 {@code count}개의 matchId를 반환한다. */
  List<String> findRecentMatchIds(String puuid, int count);

  /** {@code startTimeEpochSeconds} 이후의 matchId를 최대 {@code count}개 반환한다. */
  List<String> findMatchIdsSince(String puuid, long startTimeEpochSeconds, int count);

  /**
   * {@code startTimeEpochSeconds} ~ {@code endTimeEpochSeconds} 범위의 matchId를
   * 최대 {@code count}개 반환한다 (grace period 전용).
   */
  List<String> findMatchIdsBetween(
      String puuid, long startTimeEpochSeconds, long endTimeEpochSeconds, int count);

  // ── Match Detail ───────────────────────────────────────────────────────

  /** matchId로 match 상세 정보를 조회한다. */
  RiotMatchDto loadMatch(String matchId);

  // ── League Entries ─────────────────────────────────────────────────────

  /**
   * 리그 엔트리 목록을 조회한다.
   *
   * @param queue    큐 타입 (예: "RANKED_SOLO_5x5")
   * @param tier     티어 문자열 (예: "DIAMOND")
   * @param division 디비전 문자열 (예: "I")
   * @param page     페이지 번호 (1부터 시작)
   */
  List<LeagueEntry> loadLeagueEntries(String queue, String tier, String division, int page);
}
