package com.bestduo_BE.application;

import com.bestduo_BE.application.filter.BottomDuoDetailFilterParser;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.repository.BottomDuoMatchupStatRepository;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.domain.service.BottomDuoMatchupRules;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoDetailStatistics {

  private final BottomDuoStatRepository bottomDuoStatRepository;
  private final BottomDuoMatchupStatRepository bottomDuoMatchupStatRepository;
  private final BottomDuoDetailFilterParser bottomDuoDetailFilterParser;
  private final BottomDuoMatchupRules bottomDuoMatchupRules;
  private final ChampionMetaClient championMetaClient;

  private static final int COUNTER_COUNT = 4;

  public BottomDuoDetailStatisticsResponse handle(BottomDuoDetailStatisticsRequest request) {
    // 1) 키/필터/정렬 조건 파싱
    BottomDuoFilterCriteria criteria = bottomDuoDetailFilterParser.parse(request);

    BottomDuoStat baseStat = bottomDuoStatRepository.findOne(criteria.getAdCampionId(),
        criteria.getSupCampionId(), criteria.getTier());

    int totalGames = calculateTotalGames(criteria);

    List<BottomDuoMatchupStat> matchups =
        bottomDuoMatchupStatRepository.findBy(criteria);

    List<BottomDuoMatchupStat> counters =
        bottomDuoMatchupRules.takeLowestWinRate(matchups, COUNTER_COUNT);

    BottomDuoDetailStatisticsResponse.DuoSummary duoSummary =
        toDuoSummary(baseStat, totalGames);

    List<BottomDuoDetailStatisticsResponse.MatchupStat> matchupViews = matchups.stream()
        .map(this::toMatchupView)
        .toList();

    List<BottomDuoDetailStatisticsResponse.MatchupStat> counterViews = counters.stream()
        .map(this::toMatchupView)
        .toList();

    return new BottomDuoDetailStatisticsResponse(duoSummary, matchupViews, counterViews);
  }

  private int calculateTotalGames(BottomDuoFilterCriteria criteria) {
    BottomDuoFilterCriteria totalCriteria = new BottomDuoFilterCriteria(
        null,
        null,
        criteria.getTier(),
        criteria.getSortOption()
    );

    return bottomDuoStatRepository.findBy(totalCriteria).stream()
        .mapToInt(BottomDuoStat::getGames)
        .sum();
  }

  private BottomDuoDetailStatisticsResponse.DuoSummary toDuoSummary(BottomDuoStat baseStat,
      int totalGames) {
    var adMeta = championMetaClient.findById(baseStat.getAdChampionId());
    var supMeta = championMetaClient.findById(baseStat.getSupChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) baseStat.getGames() / totalGames;

    return new BottomDuoDetailStatisticsResponse.DuoSummary(
        adMeta.getName(),
        adMeta.getImageUrl(),
        supMeta.getName(),
        supMeta.getImageUrl(),
        baseStat.getWinRate(),
        baseStat.getGames(),
        pickRate
    );
  }

  private BottomDuoDetailStatisticsResponse.MatchupStat toMatchupView(BottomDuoMatchupStat stat) {
    var opponentAdMeta = championMetaClient.findById(stat.getOpponentAdId());
    var opponentSupMeta = championMetaClient.findById(stat.getOpponentSupId());

    return new BottomDuoDetailStatisticsResponse.MatchupStat(
        opponentAdMeta.getName(),
        opponentAdMeta.getImageUrl(),
        opponentSupMeta.getName(),
        opponentSupMeta.getImageUrl(),
        stat.getWinRate(),
        stat.getGames()
    );
  }


}
