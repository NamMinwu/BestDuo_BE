package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatAggregator;
import com.bestduo_BE.common.domain.model.Tier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateBottomDuoStatsTest {

  @Mock
  private BottomDuoStatAggregator aggregator;

  @Mock
  private ComputeBottomDuoRanking ranking;

  private AggregateBottomDuoStats useCase;

  @BeforeEach
  void setUp() {
    useCase = new AggregateBottomDuoStats(aggregator, ranking);
  }

  @Test
  @DisplayName("execute — tier가 null이면 aggregator와 ranking에 null 스코프를 전달한다")
  void executeWithNullTierPassesNullScope() {
    when(aggregator.aggregate("14.10", null)).thenReturn(10);
    when(ranking.execute("14.10", null))
        .thenReturn(new ComputeBottomDuoRanking.Result("14.10", 7));

    AggregateBottomDuoStats.Result result = useCase.execute("14.10", null);

    assertThat(result.affectedRows()).isEqualTo(10);
    assertThat(result.rankingsUpdated()).isEqualTo(7);
    verify(aggregator).aggregate("14.10", null);
    verify(ranking).execute("14.10", null);
  }

  @Test
  @DisplayName("execute — tier가 지정되면 tier.name()을 스코프로 전달한다")
  void executeWithSpecificTierPassesTierName() {
    when(aggregator.aggregate("14.10", "EMERALD")).thenReturn(5);
    when(ranking.execute("14.10", "EMERALD"))
        .thenReturn(new ComputeBottomDuoRanking.Result("14.10", 3));

    AggregateBottomDuoStats.Result result = useCase.execute("14.10", Tier.EMERALD);

    assertThat(result.affectedRows()).isEqualTo(5);
    assertThat(result.rankingsUpdated()).isEqualTo(3);
    verify(aggregator).aggregate("14.10", "EMERALD");
    verify(ranking).execute("14.10", "EMERALD");
  }

  @Test
  @DisplayName("execute — Tier.ALL_TIERS는 null 스코프로 정규화한다")
  void executeWithAllTiersNormalizesToNullScope() {
    when(aggregator.aggregate("14.10", null)).thenReturn(42);
    when(ranking.execute("14.10", null))
        .thenReturn(new ComputeBottomDuoRanking.Result("14.10", 30));

    AggregateBottomDuoStats.Result result = useCase.execute("14.10", Tier.ALL_TIERS);

    assertThat(result.affectedRows()).isEqualTo(42);
    assertThat(result.rankingsUpdated()).isEqualTo(30);
    verify(aggregator).aggregate("14.10", null);
    verify(ranking).execute("14.10", null);
  }
}
