package com.bestduo_BE.application;

import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoCounterResponse;
import com.bestduo_BE.presentation.api.dto.BottomDuoCounterResponse.Item;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoCounters {

  private static final int DEFAULT_COUNTER_SIZE = 10;

  private final BottomDuoMatchupFinder matchupFinder;
  private final ChampionMetaClient championMetaFinder;

  public BottomDuoCounterResponse execute(
      Tier tier,
      String myAdcChampionId,
      String mySupChampionId,
      Integer counterSizeOrNull
  ) {
    int counterSize = (counterSizeOrNull == null) ? DEFAULT_COUNTER_SIZE : Math.max(1, Math.min(50, counterSizeOrNull));

    int totalGames = matchupFinder.findMyDuoTotalGames(tier, myAdcChampionId, mySupChampionId);

    var myAdc = championMetaFinder.findById(myAdcChampionId);
    var mySup = championMetaFinder.findById(mySupChampionId);

    var myMeta = new BottomDuoCounterResponse.DuoMeta(
        myAdcChampionId,
        myAdc.name(),
        myAdc.imageUrl(),
        mySupChampionId,
        mySup.name(),
        mySup.imageUrl()
    );

    List<Item> counters =
        matchupFinder.findCountersByLowestWinRate(tier, myAdcChampionId, mySupChampionId, counterSize)
            .stream()
            .map(row -> toItem(row, totalGames))
            .toList();

    return new BottomDuoCounterResponse(tier.name(), totalGames, myMeta, counterSize, counters);
  }

  private BottomDuoCounterResponse.Item toItem(BottomDuoMatchupFinder.MatchupRow row, int totalGames) {
    var oppAdc = championMetaFinder.findById(row.oppAdcChampionId());
    var oppSup = championMetaFinder.findById(row.oppSupChampionId());

    double pickRate = totalGames == 0 ? 0 : (double) row.games() / totalGames;

    return new BottomDuoCounterResponse.Item(
        row.oppAdcChampionId(),
        oppAdc.name(),
        oppAdc.imageUrl(),
        row.oppSupChampionId(),
        oppSup.name(),
        oppSup.imageUrl(),
        row.winRate(),
        pickRate,
        row.games()
    );
  }
}
