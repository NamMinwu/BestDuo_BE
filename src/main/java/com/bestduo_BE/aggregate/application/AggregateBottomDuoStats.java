package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatAggregator;
import com.bestduo_BE.common.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @deprecated bottom_duo_raw 기반 stat upsert 경로. {@link AggregateBottomDuoFromMatch} 로 대체됨.
 *     {@code BottomDuoAggregateScheduler} 는 더 이상 이 클래스를 호출하지 않는다.
 *     bottom_duo_raw 테이블 자체 제거 PR 에서 함께 제거 예정.
 */
@Deprecated
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
