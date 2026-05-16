package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoMatchupAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @deprecated bottom_duo_raw self-join 기반 matchup upsert 경로. {@link AggregateBottomDuoFromMatch}
 *     로 대체됨. {@code BottomDuoAggregateScheduler} 는 더 이상 이 클래스를 호출하지 않는다.
 *     bottom_duo_raw 테이블 자체 제거 PR 에서 함께 제거 예정.
 */
@Deprecated
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
