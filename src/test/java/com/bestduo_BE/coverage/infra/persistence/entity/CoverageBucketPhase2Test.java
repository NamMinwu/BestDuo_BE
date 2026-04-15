package com.bestduo_BE.coverage.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageBucketPhase2Test {

  private CoverageBucket bucket() {
    return CoverageBucket.create("15.1", Tier.GOLD);
  }

  @Test
  @DisplayName("create() — dailySeedResetAt은 null로 초기화된다")
  void createInitializesDailySeedResetAtAsNull() {
    CoverageBucket b = bucket();

    assertThat(b.getDailySeedResetAt()).isNull();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘 이전이면 리셋한다")
  void resetDailySeedIfNeededResetsWhenOutdated() {
    CoverageBucket b = bucket();
    OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);

    b.resetDailySeedIfNeeded(yesterday, LocalDate.now());

    assertThat(b.getDailySeedResetAt()).isNotNull();
    assertThat(b.getDailyPagesProcessed()).isZero();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘이면 리셋하지 않는다")
  void resetDailySeedIfNeededSkipsWhenAlreadyResetToday() {
    CoverageBucket b = bucket();
    for (int i = 0; i < 3; i++) {
      b.incrementDailyPagesProcessed();
    }
    OffsetDateTime todayTime = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

    b.resetDailySeedIfNeeded(todayTime, LocalDate.now());

    assertThat(b.getDailyPagesProcessed()).isEqualTo(3);
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 null이면 리셋한다")
  void resetDailySeedIfNeededResetsWhenNullResetAt() {
    CoverageBucket b = bucket();
    for (int i = 0; i < 3; i++) {
      b.incrementDailyPagesProcessed();
    }

    b.resetDailySeedIfNeeded(null, LocalDate.now());

    assertThat(b.getDailyPagesProcessed()).isZero();
    assertThat(b.getDailySeedResetAt()).isNotNull();
  }
}
