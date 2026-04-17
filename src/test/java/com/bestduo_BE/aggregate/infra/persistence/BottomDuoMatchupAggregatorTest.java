package com.bestduo_BE.aggregate.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoMatchupAggregatorTest {

  @Mock
  private BottomDuoMatchupAggregateJpaRepository repository;

  private BottomDuoMatchupAggregator aggregator;

  @BeforeEach
  void setUp() {
    aggregator = new BottomDuoMatchupAggregator(repository);
  }

  @Test
  @DisplayName("aggregateAll — 레포지토리에 위임하고 집계된 행 수를 반환한다")
  void aggregateAllDelegatesToRepository() {
    given(repository.upsertAllFromRaw()).willReturn(42);

    int aggregatedRows = aggregator.aggregateAll();

    assertEquals(42, aggregatedRows);
    then(repository).should().upsertAllFromRaw();
  }
}
