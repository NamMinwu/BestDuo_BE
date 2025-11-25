package com.bestduo_BE.application;

import com.bestduo_BE.application.port.RiotApiClient;
import com.bestduo_BE.domain.model.BottomDuoMatch;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.domain.service.BottomDuoStatCalculator;
import com.bestduo_BE.infra.riot.RawMatchProcessor;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectBottomDuoStatistics {
  private final RiotApiClient riotApiClient;
  private final RawMatchProcessor rawMatchProcessor;
  private final BottomDuoStatCalculator bottomDuoStatCalculator;
  private final BottomDuoStatRepository bottomDuoStatRepository;

  public void collect(String puuid) {
    var matchIds = riotApiClient.loadMatchIdsByPuuid(puuid, 20);

    matchIds.forEach(matchId -> {
      RiotMatchDto match = riotApiClient.loadMatch(matchId);
      List<BottomDuoMatch> bottomDuoMatches = rawMatchProcessor.getMatches(match);
      List<BottomDuoStat> bottomDuoStats = bottomDuoStatCalculator.calculate(bottomDuoMatches);
      bottomDuoStatRepository.save(bottomDuoStats);
    });
  }
}