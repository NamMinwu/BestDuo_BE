package com.bestduo_BE.common.infra.riot.budget;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 1·2 일일 API 예산을 추적한다.
 * <p>자정마다 {@link DailyPipelineState}가 새로 생성되므로
 * 날짜가 바뀌면 예산은 자동으로 리셋된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyBudgetTracker {

  private final DailyPipelineStateJpaRepository stateRepository;
  private final PipelineProperties props;

  /** Stage 1(SEED) 예산이 남아있으면 true */
  public boolean canSeed() {
    return getOrCreateTodayState().getSeedApiCallsUsed() < props.getSeedDailyBudget();
  }

  /** Stage 2(COLLECT) 예산이 남아있으면 true */
  public boolean canCollect() {
    return getOrCreateTodayState().getCollectApiCallsUsed() < props.getCollectDailyBudget();
  }

  /** Stage 1 API 호출 {@code count}회를 기록하고 DB에 저장한다. */
  public void recordSeedCall(int count) {
    DailyPipelineState state = getOrCreateTodayState();
    state.incrementSeedCalls(count);
    stateRepository.save(state);
    log.debug("Seed 호출 기록: +{}, 오늘 합계={}/{}", count, state.getSeedApiCallsUsed(),
        props.getSeedDailyBudget());
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
