package com.bestduo_BE.application;

import com.bestduo_BE.application.port.BottomDuoStatAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AggregateBottomDuoStat {

  private final BottomDuoStatAggregator aggregator;

  public Result execute() {
    int affected = aggregator.aggregateAll();
    return new Result(affected);
  }

  public record Result(int affectedRows) {}
}

