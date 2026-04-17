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
class BottomDuoStatAggregatorTest {

  @Mock
  private BottomDuoStatAggregateJpaRepository repository;

  private BottomDuoStatAggregator aggregator;

  @BeforeEach
  void setUp() {
    aggregator = new BottomDuoStatAggregator(repository);
  }

  @Test
  @DisplayName("aggregate — patchVersion/tier 스코프를 레포지토리에 위임한다")
  void aggregateDelegatesToRepositoryWithScope() {
    when(repository.upsertFromRawByScope("14.10", "EMERALD")).thenReturn(42);

    int updatedRows = aggregator.aggregate("14.10", "EMERALD");

    assertThat(updatedRows).isEqualTo(42);
    verify(repository).upsertFromRawByScope("14.10", "EMERALD");
  }

  @Test
  @DisplayName("aggregate — tier가 null이면 해당 patch의 모든 tier를 집계한다")
  void aggregateWithNullTierAggregatesAllTiersForPatch() {
    when(repository.upsertFromRawByScope("14.10", null)).thenReturn(99);

    int updatedRows = aggregator.aggregate("14.10", null);

    assertThat(updatedRows).isEqualTo(99);
    verify(repository).upsertFromRawByScope("14.10", null);
  }
}
