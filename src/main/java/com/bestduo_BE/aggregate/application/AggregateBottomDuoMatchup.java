package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoMatchupAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AggregateBottomDuoMatchup {
  private final BottomDuoMatchupAggregator aggregator;

  public Result execute() {
    int affected = aggregator.aggregateAll();
    return new Result(affected);
  }

  public record Result(int affectedRows) {}
}
