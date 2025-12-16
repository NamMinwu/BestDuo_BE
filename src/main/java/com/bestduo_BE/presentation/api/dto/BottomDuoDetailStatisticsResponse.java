package com.bestduo_BE.presentation.api.dto;

import java.util.List;

public record BottomDuoDetailStatisticsResponse(
    DuoSummary duo,                  // 선택한 조합 기본 요약
    List<MatchupStat> matchups,      // 전체 매치업 리스트
    List<MatchupStat> counters       // 카운터 후보 (하위 N개)
) {

  /** 선택된 조합의 요약 정보 */
  public record DuoSummary(
      String adName,
      String adImage,
      String supName,
      String supImage,
      double winRate,       // 전체 승률
      int games, // 전체 게임 수
      double pickRate
  ) {}

  /** 상대 매치업 정보 하나 */
  public record MatchupStat(
      String opponentAdName,
      String opponentAdImage,
      String opponentSupName,
      String opponentSupImage,
      double winRate,       // 이 매치업에서의 우리 듀오 승률
      int games            // 이 매치업에서의 게임 수
  ) {}
}