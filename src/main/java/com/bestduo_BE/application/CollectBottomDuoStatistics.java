package com.bestduo_BE.application;

import com.bestduo_BE.application.port.RiotApiClient;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.repository.BottomDuoMatchupStatRepository;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.domain.service.BottomDuoMatchupAggregator;
import com.bestduo_BE.domain.service.BottomDuoStatAggregator;
import com.bestduo_BE.infra.riot.RawMatchProcessor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectBottomDuoStatistics {
  private final RiotApiClient riotApiClient;
  private final RawMatchProcessor rawMatchProcessor;
  private final BottomDuoStatAggregator bottomDuoStatAggregator;
  private final BottomDuoStatRepository bottomDuoStatRepository;
  private final BottomDuoMatchupAggregator bottomDuoMatchupAggregator;
  private final BottomDuoMatchupStatRepository bottomDuoMatchupStatRepository;

  public void collect(String puuid) {
    List<String> matchIds = riotApiClient.loadMatchIdsByPuuid(puuid, 20);

    matchIds.stream()
        .map(riotApiClient::loadMatch)
        .map(rawMatchProcessor::getMatches)
        .filter(matches -> !matches.isEmpty())
        .forEach(matches -> {
          // 사람 정보까지 얻는다
          List<BottomDuoStat> bottomDuoStats = bottomDuoStatAggregator.calculate(matches);
          List<BottomDuoMatchupStat> bottomDuoMatchupStats =
              bottomDuoMatchupAggregator.calculate(matches);

          bottomDuoStatRepository.upsertAll(bottomDuoStats);
          bottomDuoMatchupStatRepository.upsertAll(bottomDuoMatchupStats);
        });
  }


}
