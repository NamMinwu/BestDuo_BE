package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyPipelineStateTest {

  @Test
  @DisplayName("create() — 날짜별 초기 상태가 올바르게 설정된다")
  void createInitializesFields() {
    LocalDate today = LocalDate.of(2026, 4, 14);

    DailyPipelineState state = DailyPipelineState.create(today);

    assertThat(state.getPipelineDate()).isEqualTo(today);
    assertThat(state.getSeedApiCallsUsed()).isZero();
    assertThat(state.getCollectApiCallsUsed()).isZero();
    assertThat(state.getSeedCompletedTiers()).isEqualTo("[]");
    assertThat(state.getCreatedAt()).isNotNull();
    assertThat(state.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("incrementSeedCalls() — seedApiCallsUsed가 delta만큼 증가한다")
  void incrementSeedCallsAddsToCount() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());

    state.incrementSeedCalls(3);
    state.incrementSeedCalls(2);

    assertThat(state.getSeedApiCallsUsed()).isEqualTo(5);
  }

  @Test
  @DisplayName("incrementCollectCalls() — collectApiCallsUsed가 delta만큼 증가한다")
  void incrementCollectCallsAddsToCount() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());

    state.incrementCollectCalls(10);

    assertThat(state.getCollectApiCallsUsed()).isEqualTo(10);
  }

  @Test
  @DisplayName("recordSeedCompletedTier() — tier가 JSON 배열에 추가된다")
  void recordSeedCompletedTierAppendsTier() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());

    state.recordSeedCompletedTier("MASTER");
    state.recordSeedCompletedTier("CHALLENGER");

    assertThat(state.getSeedCompletedTiers()).contains("MASTER", "CHALLENGER");
  }

  @Test
  @DisplayName("recordSeedCompletedTier() — 중복 tier는 한 번만 포함된다")
  void recordSeedCompletedTierDeduplicates() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());

    state.recordSeedCompletedTier("GOLD");
    state.recordSeedCompletedTier("GOLD");

    long count = state.getSeedCompletedTiers().chars()
        .filter(c -> c == 'G')
        .count();
    // "GOLD"가 한 번만 있으면 G는 1번
    assertThat(state.getSeedCompletedTiers().indexOf("GOLD"))
        .isEqualTo(state.getSeedCompletedTiers().lastIndexOf("GOLD"));
  }
}
