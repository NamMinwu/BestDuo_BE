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
  @DisplayName("create() — dailySeedCompleted는 false, dailySeedResetAt은 null로 초기화된다")
  void createInitializesDailySeedFields() {
    CoverageBucket b = bucket();

    assertThat(b.isDailySeedCompleted()).isFalse();
    assertThat(b.getDailySeedResetAt()).isNull();
  }

  @Test
  @DisplayName("markDailySeedCompleted() — dailySeedCompleted가 true로 설정된다")
  void markDailySeedCompletedSetsFlag() {
    CoverageBucket b = bucket();

    b.markDailySeedCompleted();

    assertThat(b.isDailySeedCompleted()).isTrue();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘 이전이면 리셋한다")
  void resetDailySeedIfNeededResetsWhenOutdated() {
    CoverageBucket b = bucket();
    b.markDailySeedCompleted();
    // 어제 리셋 시점 설정 (오늘보다 이전)
    OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);
    b.resetDailySeedIfNeeded(yesterday, LocalDate.now());

    assertThat(b.isDailySeedCompleted()).isFalse();
    assertThat(b.getDailySeedResetAt()).isNotNull();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘이면 리셋하지 않는다")
  void resetDailySeedIfNeededSkipsWhenAlreadyResetToday() {
    CoverageBucket b = bucket();
    b.markDailySeedCompleted();
    OffsetDateTime todayTime = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
    b.resetDailySeedIfNeeded(todayTime, LocalDate.now());

    // 이미 오늘 리셋했으면 completed가 true 유지
    assertThat(b.isDailySeedCompleted()).isTrue();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 null이면 리셋한다")
  void resetDailySeedIfNeededResetsWhenNullResetAt() {
    CoverageBucket b = bucket();
    b.markDailySeedCompleted();

    b.resetDailySeedIfNeeded(null, LocalDate.now());

    assertThat(b.isDailySeedCompleted()).isFalse();
  }
}
