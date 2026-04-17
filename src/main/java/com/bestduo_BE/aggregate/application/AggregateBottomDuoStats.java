package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AggregateBottomDuoStats {

  private final BottomDuoStatAggregator aggregator;
  private final ComputeBottomDuoRanking ranking;

  public Result execute() {
    int affected = aggregator.aggregateAll();
    ComputeBottomDuoRanking.Result rankingResult = ranking.execute();
    return new Result(affected, rankingResult.updatedRows());
  }

  public record Result(int affectedRows, int rankingsUpdated) {}
}
