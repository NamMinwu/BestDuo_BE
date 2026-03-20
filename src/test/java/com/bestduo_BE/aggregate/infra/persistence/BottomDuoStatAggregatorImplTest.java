package com.bestduo_BE.aggregate.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoStatAggregatorImplTest {

  @Mock
  private BottomDuoStatAggJpaRepository repository;

  private BottomDuoStatAggregatorImpl aggregator;

  @BeforeEach
  void setUp() {
    aggregator = new BottomDuoStatAggregatorImpl(repository);
  }

  @Test
  void aggregateAllDelegatesToRepository() {
    when(repository.upsertAllFromRaw()).thenReturn(42);

    int updatedRows = aggregator.aggregateAll();

    assertThat(updatedRows).isEqualTo(42);
    verify(repository).upsertAllFromRaw();
  }
}
