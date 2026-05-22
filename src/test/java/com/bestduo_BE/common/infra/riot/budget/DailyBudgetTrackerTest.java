package com.bestduo_BE.common.infra.riot.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyBudgetTrackerTest {

  @Mock
  private DailyPipelineStateJpaRepository stateRepository;

  private PipelineProperties props;
  private DailyBudgetTracker tracker;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setCollectDailyBudget(200);
    tracker = new DailyBudgetTracker(stateRepository, props);
  }

  @Test
  @DisplayName("collect 예산을 모두 소진하면 canCollect가 false")
  void canCollect_whenBudgetExhausted_returnsFalse() {
    DailyPipelineState exhausted = DailyPipelineState.create(LocalDate.now());
    exhausted.incrementCollectCalls(200);
    given(stateRepository.getOrCreateForDate(any(LocalDate.class))).willReturn(exhausted);

    assertThat(tracker.canCollect()).isFalse();
  }

  @Test
  @DisplayName("collect 예산이 남아있으면 canCollect가 true")
  void canCollect_whenBudgetRemains_returnsTrue() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.incrementCollectCalls(50);
    given(stateRepository.getOrCreateForDate(any(LocalDate.class))).willReturn(state);

    assertThat(tracker.canCollect()).isTrue();
  }

  @Test
  @DisplayName("recordCollectCall은 DailyPipelineState를 저장한다")
  void recordCollectCall_persistsUpdatedState() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(stateRepository.getOrCreateForDate(any(LocalDate.class))).willReturn(state);
    given(stateRepository.save(any(DailyPipelineState.class))).willReturn(state);

    tracker.recordCollectCall(3);

    verify(stateRepository).save(state);
    assertThat(state.getCollectApiCallsUsed()).isEqualTo(3);
  }
}
