package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.application.port.BottomDuoStatAggregator;
import com.bestduo_BE.common.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AggregateBottomDuoStats {

  private final BottomDuoStatAggregator aggregator;
  private final ComputeBottomDuoRanking ranking;

  public Result execute(String patchVersion, Tier tier) {
    String tierScope = normalizeTierScope(tier);
    int affected = aggregator.aggregate(patchVersion, tierScope);
    ComputeBottomDuoRanking.Result rankingResult = ranking.execute(patchVersion, tierScope);
    return new Result(affected, rankingResult.updatedRows());
  }

  private String normalizeTierScope(Tier tier) {
    if (tier == null || tier == Tier.ALL_TIERS) {
      return null;
    }
    return tier.name();
  }

  public record Result(int affectedRows, int rankingsUpdated) {}
}
