package com.bestduo_BE.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.infra.persistence.repository.BottomDuoMatchupAggJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoMatchupAggregatorImplTest {

  @Mock
  private BottomDuoMatchupAggJpaRepository repository;

  private BottomDuoMatchupAggregatorImpl aggregator;

  @BeforeEach
  void setUp() {
    aggregator = new BottomDuoMatchupAggregatorImpl(repository);
  }

  @Test
  void aggregateAllDelegatesToRepository() {
    given(repository.upsertAllFromRaw()).willReturn(42);

    int aggregatedRows = aggregator.aggregateAll();

    assertEquals(42, aggregatedRows);
    then(repository).should().upsertAllFromRaw();
  }
}
