package com.bestduo_BE.aggregate.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoStatAggregatorImplTest {

  @Mock
  private BottomDuoStatAggregateJpaRepository repository;

  private BottomDuoStatAggregatorImpl aggregator;

  @BeforeEach
  void setUp() {
    aggregator = new BottomDuoStatAggregatorImpl(repository);
  }

  @Test
  @DisplayName("aggregateAll — 레포지토리에 위임하고 업데이트된 행 수를 반환한다")
  void aggregateAllDelegatesToRepository() {
    when(repository.upsertAllFromRaw()).thenReturn(42);

    int updatedRows = aggregator.aggregateAll();

    assertThat(updatedRows).isEqualTo(42);
    verify(repository).upsertAllFromRaw();
  }
}
