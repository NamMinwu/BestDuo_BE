package com.bestduo_BE.application;

import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import com.bestduo_BE.application.filter.BottomDuoFilterParser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoStatistics {
  private final BottomDuoStatRepository bottomDuoStatRepository;
  private final BottomDuoFilterParser bottomDuoFilterParser;
  private final ChampionMetaClient championMetaClient;

  public BottomDuoStatisticsResponse handle(BottomDuoStatisticsRequest request) {
    BottomDuoFilterCriteria criteria = bottomDuoFilterParser.parse(request);
    List<BottomDuoStat> stats = bottomDuoStatRepository.findBy(criteria);
    int totalGames = stats.stream().mapToInt(BottomDuoStat::getGames).sum();
    List<BottomDuoStatisticsResponse.BottomDuoStatView> views = stats.stream()
        .map(stat -> toView(stat, totalGames))
        .toList();

    return new BottomDuoStatisticsResponse(totalGames, views);
  }

  private BottomDuoStatisticsResponse.BottomDuoStatView toView(BottomDuoStat stat, int totalGames) {
    var adMeta = championMetaClient.findById(stat.getAdChampionId());
    var supMeta = championMetaClient.findById(stat.getSupChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) stat.getGames() / totalGames;

    return new BottomDuoStatisticsResponse.BottomDuoStatView(
        adMeta.getName(),
        adMeta.getImageUrl(),
        supMeta.getName(),
        supMeta.getImageUrl(),
        stat.getWinRate(),
        stat.getGames(),
        pickRate
    );
  }
}
