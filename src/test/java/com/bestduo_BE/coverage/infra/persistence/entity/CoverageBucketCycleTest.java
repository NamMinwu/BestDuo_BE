package com.bestduo_BE.coverage.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverageBucketCycleTest {

  private CoverageBucket bucket() {
    return CoverageBucket.create("15.7", Tier.DIAMOND);
  }

  // ── advanceToNextDivision ──────────────────────────────────────────

  @Test
  @DisplayName("advanceToNextDivision() — I에서 호출하면 II/1로 이동한다")
  void advanceToNextDivision_fromI_movesToII() {
    CoverageBucket b = bucket(); // 기본 I/1
    b.advanceToNextDivision();

    assertThat(b.getCurrentDivision()).isEqualTo("II");
    assertThat(b.getCurrentPage()).isEqualTo(1);
  }

  @Test
  @DisplayName("advanceToNextDivision() — II에서 호출하면 III/1로 이동한다")
  void advanceToNextDivision_fromII_movesToIII() {
    CoverageBucket b = bucket();
    b.advanceToNextDivision(); // I → II

    b.advanceToNextDivision(); // II → III

    assertThat(b.getCurrentDivision()).isEqualTo("III");
    assertThat(b.getCurrentPage()).isEqualTo(1);
  }

  @Test
  @DisplayName("advanceToNextDivision() — III에서 호출하면 IV/1로 이동한다")
  void advanceToNextDivision_fromIII_movesToIV() {
    CoverageBucket b = bucket();
    b.advanceToNextDivision(); // I → II
    b.advanceToNextDivision(); // II → III

    b.advanceToNextDivision(); // III → IV

    assertThat(b.getCurrentDivision()).isEqualTo("IV");
    assertThat(b.getCurrentPage()).isEqualTo(1);
  }

  @Test
  @DisplayName("advanceToNextDivision() — IV에서 호출하면 I/1로 wrap되고 divisionCycleCount가 증가한다")
  void advanceToNextDivision_fromIV_wrapsToIAndIncrementsCycleCount() {
    CoverageBucket b = bucket();
    b.advanceToNextDivision(); // I → II
    b.advanceToNextDivision(); // II → III
    b.advanceToNextDivision(); // III → IV

    b.advanceToNextDivision(); // IV → I (사이클 완주)

    assertThat(b.getCurrentDivision()).isEqualTo("I");
    assertThat(b.getCurrentPage()).isEqualTo(1);
    assertThat(b.getDivisionCycleCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("advanceToNextDivision() — 사이클이 아닌 전환에서는 divisionCycleCount가 증가하지 않는다")
  void advanceToNextDivision_nonWrap_doesNotIncrementCycleCount() {
    CoverageBucket b = bucket();

    b.advanceToNextDivision(); // I → II

    assertThat(b.getDivisionCycleCount()).isEqualTo(0);
  }

  // ── hasRemainingDailyQuota ─────────────────────────────────────────

  @Test
  @DisplayName("hasRemainingDailyQuota() — 처리량이 quota 미만이면 true")
  void hasRemainingDailyQuota_belowQuota_returnsTrue() {
    CoverageBucket b = bucket(); // dailyPagesProcessed = 0

    assertThat(b.hasRemainingDailyQuota(10)).isTrue();
  }

  @Test
  @DisplayName("hasRemainingDailyQuota() — 처리량이 quota와 같으면 false")
  void hasRemainingDailyQuota_equalsQuota_returnsFalse() {
    CoverageBucket b = bucket();
    for (int i = 0; i < 10; i++) {
      b.incrementDailyPagesProcessed();
    }

    assertThat(b.hasRemainingDailyQuota(10)).isFalse();
  }

  @Test
  @DisplayName("hasRemainingDailyQuota() — 처리량이 quota 초과이면 false")
  void hasRemainingDailyQuota_exceedsQuota_returnsFalse() {
    CoverageBucket b = bucket();
    for (int i = 0; i < 11; i++) {
      b.incrementDailyPagesProcessed();
    }

    assertThat(b.hasRemainingDailyQuota(10)).isFalse();
  }

  // ── incrementDailyPagesProcessed ──────────────────────────────────

  @Test
  @DisplayName("incrementDailyPagesProcessed() — 호출할 때마다 dailyPagesProcessed가 1씩 증가한다")
  void incrementDailyPagesProcessed_incrementsByOne() {
    CoverageBucket b = bucket();

    b.incrementDailyPagesProcessed();
    b.incrementDailyPagesProcessed();

    assertThat(b.getDailyPagesProcessed()).isEqualTo(2);
  }

  // ── resetDailyPagesIfNeeded (Phase 2 동작) ────────────────────────

  @Test
  @DisplayName("resetDailyPagesIfNeeded() — dailyPagesProcessed를 0으로 리셋하고 currentPage·currentDivision은 유지한다")
  void resetDailyPagesIfNeeded_resetsPagesProcessed_preservesPagePosition() {
    CoverageBucket b = bucket();
    b.advanceToNextDivision(); // I → II
    b.advancePageState(100);   // currentPage = 2
    for (int i = 0; i < 5; i++) {
      b.incrementDailyPagesProcessed();
    }

    OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);
    b.resetDailyPagesIfNeeded(yesterday, LocalDate.now());

    assertThat(b.getDailyPagesProcessed()).isEqualTo(0);
    assertThat(b.getCurrentDivision()).isEqualTo("II");
    assertThat(b.getCurrentPage()).isEqualTo(2);
  }

  @Test
  @DisplayName("resetDailyPagesIfNeeded() — 오늘 이미 리셋했으면 dailyPagesProcessed를 변경하지 않는다")
  void resetDailyPagesIfNeeded_alreadyResetToday_doesNotReset() {
    CoverageBucket b = bucket();
    for (int i = 0; i < 5; i++) {
      b.incrementDailyPagesProcessed();
    }

    OffsetDateTime todayMidnight = LocalDate.now().atStartOfDay()
        .atOffset(OffsetDateTime.now().getOffset());
    b.resetDailyPagesIfNeeded(todayMidnight, LocalDate.now());

    assertThat(b.getDailyPagesProcessed()).isEqualTo(5);
  }
}
