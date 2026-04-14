package com.bestduo_BE.coverage.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageBucketPhase2Test {

  private CoverageBucket bucket() {
    return CoverageBucket.create("15.1", Tier.GOLD, 1000L, 1);
  }

  @Test
  @DisplayName("create() — dailySeedCompleted는 false, dailySeedResetAt은 null로 초기화된다")
  void createInitializesDailySeedFields() {
    CoverageBucket b = bucket();

    assertThat(b.isDailySeedCompleted()).isFalse();
    assertThat(b.getDailySeedResetAt()).isNull();
    assertThat(b.getDailySeedStepsProcessed()).isZero();
  }

  @Test
  @DisplayName("markDailySeedCompleted() — dailySeedCompleted가 true로 설정된다")
  void markDailySeedCompletedSetsFlag() {
    CoverageBucket b = bucket();

    b.markDailySeedCompleted();

    assertThat(b.isDailySeedCompleted()).isTrue();
  }

  @Test
  @DisplayName("advanceToNextPage() — 현재 division을 유지하고 page만 증가시킨다")
  void advanceToNextPageKeepsDivisionAndIncrementsPage() {
    CoverageBucket b = bucket();
    b.advanceToNextPage();

    assertThat(b.getSeedDivision()).isEqualTo("I");
    assertThat(b.getSeedPage()).isEqualTo(2);
  }

  @Test
  @DisplayName("advanceToNextDivisionStart() — 현재 division이 끝나면 다음 division 1페이지로 이동한다")
  void advanceToNextDivisionStartMovesToNextDivision() {
    CoverageBucket b = bucket();
    b.advanceToNextPage();
    b.advanceToNextPage();
    b.advanceToNextDivisionStart();

    assertThat(b.getSeedDivision()).isEqualTo("II");
    assertThat(b.getSeedPage()).isEqualTo(1);
  }

  @Test
  @DisplayName("advanceToNextDivisionStart() — IV division 다음은 I / 1 로 순환한다")
  void advanceToNextDivisionStartWrapsFromIvToI() {
    CoverageBucket b = bucket();
    b.advanceToNextDivisionStart();
    b.advanceToNextDivisionStart();
    b.advanceToNextDivisionStart();
    b.advanceToNextDivisionStart();

    assertThat(b.getSeedDivision()).isEqualTo("I");
    assertThat(b.getSeedPage()).isEqualTo(1);
  }

  @Test
  @DisplayName("incrementDailySeedProgress() — 하루 step 진행량을 1 증가시킨다")
  void incrementDailySeedProgressIncrementsSteps() {
    CoverageBucket b = bucket();

    b.incrementDailySeedProgress();

    assertThat(b.getDailySeedStepsProcessed()).isEqualTo(1);
    assertThat(b.isDailyQuotaReached(1)).isTrue();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘 이전이면 step과 완료 상태를 리셋하고 cursor는 유지한다")
  void resetDailySeedIfNeededResetsWhenOutdatedButPreservesCursor() {
    CoverageBucket b = bucket();
    b.advanceToNextDivisionStart();
    b.advanceToNextPage();
    b.incrementDailySeedProgress();
    b.incrementDailySeedProgress();
    b.markDailySeedCompleted();
    OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);

    b.resetDailySeedIfNeeded(yesterday, LocalDate.now());

    assertThat(b.isDailySeedCompleted()).isFalse();
    assertThat(b.getDailySeedStepsProcessed()).isZero();
    assertThat(b.getSeedDivision()).isEqualTo("II");
    assertThat(b.getSeedPage()).isEqualTo(2);
    assertThat(b.getDailySeedResetAt()).isNotNull();
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 오늘이면 리셋하지 않는다")
  void resetDailySeedIfNeededSkipsWhenAlreadyResetToday() {
    CoverageBucket b = bucket();
    b.incrementDailySeedProgress();
    b.markDailySeedCompleted();
    OffsetDateTime todayTime = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

    b.resetDailySeedIfNeeded(todayTime, LocalDate.now());

    assertThat(b.isDailySeedCompleted()).isTrue();
    assertThat(b.getDailySeedStepsProcessed()).isEqualTo(1);
  }

  @Test
  @DisplayName("resetDailySeedIfNeeded() — dailySeedResetAt이 null이면 리셋한다")
  void resetDailySeedIfNeededResetsWhenNullResetAt() {
    CoverageBucket b = bucket();
    b.incrementDailySeedProgress();
    b.markDailySeedCompleted();

    b.resetDailySeedIfNeeded(null, LocalDate.now());

    assertThat(b.isDailySeedCompleted()).isFalse();
    assertThat(b.getDailySeedStepsProcessed()).isZero();
  }
}
