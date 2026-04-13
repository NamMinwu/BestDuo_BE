package com.bestduo_BE.common.infra.riot.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import java.time.LocalDate;
import java.util.Optional;
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
    props.setSeedDailyBudget(100);
    props.setCollectDailyBudget(200);
    tracker = new DailyBudgetTracker(stateRepository, props);
  }

  @Test
  @DisplayName("오늘 상태가 없으면 새로 생성하고 seed 예산이 남아있다")
  void canSeed_whenNoStateExists_createsStateAndAllowsSeed() {
    DailyPipelineState newState = DailyPipelineState.create(LocalDate.now());
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.empty());
    given(stateRepository.save(any(DailyPipelineState.class))).willReturn(newState);

    assertThat(tracker.canSeed()).isTrue();
  }

  @Test
  @DisplayName("seed 예산을 모두 소진하면 canSeed가 false")
  void canSeed_whenBudgetExhausted_returnsFalse() {
    DailyPipelineState exhausted = DailyPipelineState.create(LocalDate.now());
    exhausted.incrementSeedCalls(100); // 예산 상한 100과 동일
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.of(exhausted));

    assertThat(tracker.canSeed()).isFalse();
  }

  @Test
  @DisplayName("collect 예산을 모두 소진하면 canCollect가 false")
  void canCollect_whenBudgetExhausted_returnsFalse() {
    DailyPipelineState exhausted = DailyPipelineState.create(LocalDate.now());
    exhausted.incrementCollectCalls(200);
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.of(exhausted));

    assertThat(tracker.canCollect()).isFalse();
  }

  @Test
  @DisplayName("collect 예산이 남아있으면 canCollect가 true")
  void canCollect_whenBudgetRemains_returnsTrue() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.incrementCollectCalls(50);
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.of(state));

    assertThat(tracker.canCollect()).isTrue();
  }

  @Test
  @DisplayName("recordSeedCall은 DailyPipelineState를 저장한다")
  void recordSeedCall_persistsUpdatedState() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.of(state));
    given(stateRepository.save(any(DailyPipelineState.class))).willReturn(state);

    tracker.recordSeedCall(1);

    verify(stateRepository).save(state);
    assertThat(state.getSeedApiCallsUsed()).isEqualTo(1);
  }

  @Test
  @DisplayName("recordCollectCall은 DailyPipelineState를 저장한다")
  void recordCollectCall_persistsUpdatedState() {
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.of(state));
    given(stateRepository.save(any(DailyPipelineState.class))).willReturn(state);

    tracker.recordCollectCall(3);

    verify(stateRepository).save(state);
    assertThat(state.getCollectApiCallsUsed()).isEqualTo(3);
  }

  @Test
  @DisplayName("getOrCreateTodayState는 없으면 새 상태를 저장하고 반환한다")
  void getOrCreateTodayState_whenAbsent_savesAndReturnsNew() {
    DailyPipelineState newState = DailyPipelineState.create(LocalDate.now());
    given(stateRepository.findByPipelineDate(any(LocalDate.class))).willReturn(Optional.empty());
    given(stateRepository.save(any(DailyPipelineState.class))).willReturn(newState);

    DailyPipelineState result = tracker.getOrCreateTodayState();

    verify(stateRepository).save(any(DailyPipelineState.class));
    assertThat(result).isNotNull();
  }
}
