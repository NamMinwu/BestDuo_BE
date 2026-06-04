package com.bestduo_BE.common.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatabaseSizeMetricsTest {

  private static final String QUERY = "SELECT pg_database_size(current_database())";
  private static final String GAUGE = "bestduo.db.size";

  @Mock
  private JdbcTemplate jdbcTemplate;

  private SimpleMeterRegistry registry;
  private DatabaseSizeMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new DatabaseSizeMetrics(registry, jdbcTemplate);
  }

  @Test
  @DisplayName("refresh 는 pg_database_size 조회 결과를 게이지에 반영한다")
  void refresh_updatesGaugeWithQueriedSize() {
    // Arrange
    when(jdbcTemplate.queryForObject(eq(QUERY), eq(Long.class))).thenReturn(2_287L * 1024 * 1024);

    // Act
    metrics.refresh();

    // Assert
    assertThat(registry.get(GAUGE).gauge().value()).isEqualTo(2_287d * 1024 * 1024);
  }

  @Test
  @DisplayName("등록 직후 갱신 전에는 게이지가 0 이다")
  void gauge_isZeroBeforeFirstRefresh() {
    assertThat(registry.get(GAUGE).gauge().value()).isZero();
  }

  @Test
  @DisplayName("조회가 실패해도 예외를 전파하지 않고 직전 값을 유지한다")
  void refresh_onQueryFailure_keepsPreviousValueAndDoesNotThrow() {
    // Arrange — 먼저 정상 값으로 한 번 채운다
    when(jdbcTemplate.queryForObject(eq(QUERY), eq(Long.class)))
        .thenReturn(1_000L)
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));
    metrics.refresh();

    // Act — 두 번째 호출은 실패하지만 예외가 새어나오면 안 된다
    metrics.refresh();

    // Assert — 직전 값(1000) 유지
    assertThat(registry.get(GAUGE).gauge().value()).isEqualTo(1_000d);
  }
}
