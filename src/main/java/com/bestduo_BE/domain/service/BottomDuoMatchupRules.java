package com.bestduo_BE.domain.service;

import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BottomDuoMatchupRules {
  /** 최저 승률 N개를 카운터 후보로 선정 */
  public List<BottomDuoMatchupStat> takeLowestWinRate(
      List<BottomDuoMatchupStat> matchups,
      int n
  ) {
    return matchups.stream()
        .sorted(Comparator.comparingDouble(BottomDuoMatchupStat::getWins))
        .limit(n)
        .toList();
  }
}
