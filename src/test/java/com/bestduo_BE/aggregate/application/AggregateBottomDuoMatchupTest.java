package com.bestduo_BE.aggregate.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoMatchupAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateBottomDuoMatchupTest {

  @Mock
  private BottomDuoMatchupAggregator aggregator;

  private AggregateBottomDuoMatchup useCase;

  @BeforeEach
  void setUp() {
    useCase = new AggregateBottomDuoMatchup(aggregator);
  }

  @Test
  @DisplayName("execute — 집계를 실행하고 영향받은 행 수를 반환한다")
  void executeReturnsAffectedRows() {
    given(aggregator.aggregateAll()).willReturn(73);

    AggregateBottomDuoMatchup.Result result = useCase.execute();

    assertEquals(73, result.affectedRows());
    then(aggregator).should().aggregateAll();
  }
}
