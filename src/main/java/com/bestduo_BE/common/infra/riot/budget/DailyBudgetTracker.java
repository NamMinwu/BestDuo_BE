package com.bestduo_BE.common.infra.riot.budget;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 2(COLLECT_MATCH_IDS) 일일 API 예산을 추적한다.
 *
 * <p>Stage 1 은 platform(kr.api) 전용 limiter 의 자연 throttle 에 의존하므로 cap 이 없고,
 * 진행 상태 (`seedCompletedTiers`) 는 {@code DailyLeagueEntriesRunner} 가
 * {@link DailyPipelineStateJpaRepository} 를 통해 직접 관리한다.
 *
 * <p>자정마다 {@link DailyPipelineState} row 가 새로 생성되므로 날짜가 바뀌면 카운터가 자동으로 리셋된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyBudgetTracker {

  private final DailyPipelineStateJpaRepository stateRepository;
  private final PipelineProperties props;

  /** Stage 2(COLLECT) 예산이 남아있으면 true */
  public boolean canCollect() {
    return stateRepository.getOrCreateForDate(LocalDate.now())
        .getCollectApiCallsUsed() < props.getCollectDailyBudget();
  }

  /** Stage 2 API 호출 {@code count}회를 기록하고 DB에 저장한다. */
  public void recordCollectCall(int count) {
    DailyPipelineState state = stateRepository.getOrCreateForDate(LocalDate.now());
    state.incrementCollectCalls(count);
    stateRepository.save(state);
    log.debug("Collect 호출 기록: +{}, 오늘 합계={}/{}", count, state.getCollectApiCallsUsed(),
        props.getCollectDailyBudget());
  }
}
