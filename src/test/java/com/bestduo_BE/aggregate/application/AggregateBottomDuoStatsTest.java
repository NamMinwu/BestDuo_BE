package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.application.port.BottomDuoStatAggregator;
import com.bestduo_BE.common.domain.model.Tier;
import org.junit.jupiter.api.BeforeEach;
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
  void executeUsesSamePatchAndTierScopeForAggregationAndRanking() {
    when(aggregator.aggregate("14.10", "EMERALD")).thenReturn(12);
    when(ranking.execute("14.10", "EMERALD"))
        .thenReturn(new ComputeBottomDuoRanking.Result("14.10", 7));

    AggregateBottomDuoStats.Result result = useCase.execute("14.10", Tier.EMERALD);

    assertThat(result.affectedRows()).isEqualTo(12);
    assertThat(result.rankingsUpdated()).isEqualTo(7);
    verify(aggregator).aggregate("14.10", "EMERALD");
    verify(ranking).execute("14.10", "EMERALD");
  }

  @Test
  void executeTreatsAllTiersAsPatchWideScope() {
    when(aggregator.aggregate("14.10", null)).thenReturn(25);
    when(ranking.execute("14.10", null))
        .thenReturn(new ComputeBottomDuoRanking.Result("14.10", 18));

    AggregateBottomDuoStats.Result result = useCase.execute("14.10", Tier.ALL_TIERS);

    assertThat(result.affectedRows()).isEqualTo(25);
    assertThat(result.rankingsUpdated()).isEqualTo(18);
    verify(aggregator).aggregate("14.10", null);
    verify(ranking).execute("14.10", null);
  }
}
