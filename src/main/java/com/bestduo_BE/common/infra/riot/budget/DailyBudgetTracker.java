package com.bestduo_BE.common.infra.riot.budget;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 2 일일 API 예산 및 Stage 1 호출량 (모니터링용) 을 추적한다.
 * <p>Stage 1 은 platform(kr.api) 전용 limiter 의 자연 throttle 에 의존하므로
 * 일일 cap 없이 호출 카운트만 누적한다 (운영 가시성 목적).
 * <p>자정마다 {@link DailyPipelineState}가 새로 생성되므로
 * 날짜가 바뀌면 카운터는 자동으로 리셋된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyBudgetTracker {

  private final DailyPipelineStateJpaRepository stateRepository;
  private final PipelineProperties props;

  /** Stage 2(COLLECT) 예산이 남아있으면 true */
  public boolean canCollect() {
    return getOrCreateTodayState().getCollectApiCallsUsed() < props.getCollectDailyBudget();
  }

  /** Stage 1 API 호출 {@code count}회를 기록하고 DB에 저장한다 (모니터링용). */
  public void recordSeedCall(int count) {
    recordSeedCall(count, null);
  }

  /**
   * Stage 1 API 호출 {@code count}회를 기록하고, 완료된 apex 티어가 있으면 함께 저장한다 (모니터링용).
   * 단일 DB fetch로 seedApiCallsUsed와 seedCompletedTiers를 원자적으로 저장한다.
   */
  public void recordSeedCall(int count, Tier completedTier) {
    DailyPipelineState state = getOrCreateTodayState();
    state.incrementSeedCalls(count);
    if (completedTier != null) {
      state.recordSeedCompletedTier(completedTier);
    }
    stateRepository.save(state);
    log.debug("Seed 호출 기록: +{}, 완료 티어={}, 오늘 누적={}", count, completedTier,
        state.getSeedApiCallsUsed());
  }

  /** Stage 2 API 호출 {@code count}회를 기록하고 DB에 저장한다. */
  public void recordCollectCall(int count) {
    DailyPipelineState state = getOrCreateTodayState();
    state.incrementCollectCalls(count);
    stateRepository.save(state);
    log.debug("Collect 호출 기록: +{}, 오늘 합계={}/{}", count, state.getCollectApiCallsUsed(),
        props.getCollectDailyBudget());
  }

  /**
   * 오늘 날짜의 {@link DailyPipelineState}를 조회하거나 없으면 새로 생성한다.
   * 재시작 복구에도 사용된다.
   */
  public DailyPipelineState getOrCreateTodayState() {
    LocalDate today = LocalDate.now();
    return stateRepository.findByPipelineDate(today)
        .orElseGet(() -> stateRepository.save(DailyPipelineState.create(today)));
  }
}
