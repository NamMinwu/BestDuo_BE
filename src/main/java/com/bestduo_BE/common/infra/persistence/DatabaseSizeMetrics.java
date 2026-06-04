package com.bestduo_BE.common.infra.persistence;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 현재 데이터베이스의 논리 크기({@code pg_database_size})를 게이지로 노출한다.
 *
 * <p>Prometheus 노출명: {@code bestduo_db_size_bytes}. Grafana 등에서
 * {@code bestduo_db_size_bytes / <volume_bytes> > 0.9} 같은 룰로 디스크 임계 알림에 활용한다.
 *
 * <p>주의: 볼륨 파일시스템 사용률(WAL 등 포함)과 정확히 일치하지는 않는 <b>근사치</b>다.
 * 실제 디스크 % 기준 알림은 Railway 네이티브 볼륨 알림을 함께 사용한다.
 *
 * <p>매 스크랩마다 쿼리하지 않도록 값을 캐시하고 스케줄로만 갱신한다.
 */
@Component
@Slf4j
public class DatabaseSizeMetrics {

  private static final String DB_SIZE = "bestduo.db.size";

  private final JdbcTemplate jdbcTemplate;
  private final AtomicLong dbSizeBytes = new AtomicLong(0L);

  public DatabaseSizeMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    // strongReference(true): PipelineMetrics 와 동일 — supplier 가 GC 되어 값이 NaN 으로 떨어지는 것을 방지.
    Gauge.builder(DB_SIZE, dbSizeBytes, AtomicLong::doubleValue)
        .baseUnit("bytes")
        .description("current database size in bytes (pg_database_size)")
        .strongReference(true)
        .register(registry);
  }

  /** 기본 30분 주기로 DB 크기를 갱신한다. fixedRate 라 기동 직후 1회 실행되어 값이 즉시 채워진다. */
  @Scheduled(fixedRateString = "${monitoring.db-size.refresh-ms:1800000}")
  public void refresh() {
    try {
      Long bytes = jdbcTemplate.queryForObject(
          "SELECT pg_database_size(current_database())", Long.class);
      if (bytes != null) {
        dbSizeBytes.set(bytes);
      }
    } catch (RuntimeException ex) {
      // 측정 실패가 애플리케이션에 영향을 주지 않도록 마지막 값을 유지한 채 경고만 남긴다.
      log.warn("[DatabaseSizeMetrics] pg_database_size 조회 실패 — 직전 값 유지", ex);
    }
  }
}
