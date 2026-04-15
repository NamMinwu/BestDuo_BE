package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestRunner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 단일 가상 스레드 파이프라인 루프.
 *
 * <p>Stage 우선순위:
 * <ol>
 *   <li>Stage 1 — {@link DailyLeagueEntriesRunner}: summoner 등록/갱신 (일일 예산 소진 전)</li>
 *   <li>Stage 2 — {@link CollectMatchIdsRunner}: matchIds 수집 (일일 예산 소진 전)</li>
 *   <li>Stage 3 — {@link MatchIngestRunner}: match 상세 수집 (상시)</li>
 * </ol>
 *
 * <p>429 발생 시 {@code rateLimitSleepMs}(기본 60초) sleep 후 재시도.
 * match_queue가 비어있으면 {@code pollingIntervalMs} sleep.
 */
@Component
@ConditionalOnProperty(prefix = "pipeline.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PipelineRunner {

  private static final long RATE_LIMIT_SLEEP_MS = 60_000L;

  private final DailyLeagueEntriesRunner dailyLeagueEntriesRunner;
  private final CollectMatchIdsRunner collectMatchIdsRunner;
  private final MatchIngestRunner matchIngestRunner;
  private final PatchVersionService patchVersionService;
  private final PipelineProperties props;

  private Thread pipelineThread;

  @PostConstruct
  public void start() {
    pipelineThread = Thread.ofVirtual().name("pipeline-runner").start(this::loop);
  }

  @PreDestroy
  public void stop() {
    if (pipelineThread != null) {
      pipelineThread.interrupt();
      log.info("PipelineRunner 종료 요청 (interrupt)");
      try {
        pipelineThread.join(30_000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (pipelineThread.isAlive()) {
        log.warn("PipelineRunner 30초 내 종료되지 않음");
      }
    }
  }

  private void loop() {
    log.info("PipelineRunner 시작 (가상 스레드)");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        executeTick();
      } catch (RiotRateLimitedException e) {
        log.warn("429 Rate limited. {}ms 대기 후 재시도", RATE_LIMIT_SLEEP_MS);
        sleep(RATE_LIMIT_SLEEP_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("PipelineRunner 종료 (interrupted)");
        break;
      } catch (Exception e) {
        log.error("PipelineRunner 예기치 않은 오류. {}ms 후 재시도", props.getErrorBackoffMs(), e);
        sleep(props.getErrorBackoffMs());
      }
    }
  }

  /**
   * Stage 1 → 2 → 3 우선순위 판단 후 한 단위를 실행한다.
   * 테스트에서 직접 호출 가능하도록 package-private.
   */
  void executeTick() throws InterruptedException {
    // Stage 1: Seed (일일 예산 내)
    if (dailyLeagueEntriesRunner.hasWorkToday()) {
      log.debug("Stage 1 실행");
      dailyLeagueEntriesRunner.runNextChunk();
      return;
    }

    // Stage 2: CollectMatchIds (일일 예산 내)
    if (collectMatchIdsRunner.hasPending()) {
      log.debug("Stage 2 실행");
      collectMatchIdsRunner.runBatch();
      return;
    }

    // Stage 3: Ingest (상시, patch+tier 우선순위)
    // Stage 2가 유효 패치 기준으로 match ID를 수집하므로, Stage 3도 같은 기준을 사용해야 우선순위가 일치한다.
    String effectivePatch = patchVersionService.resolveEffectivePatchContext()
        .map(EffectivePatchContext::patch)
        .orElse(null);
    Tier priorityTier = props.getStage3PriorityTier();
    log.debug("Stage 3 실행 (effectivePatch={}, priorityTier={})", effectivePatch, priorityTier);
    MatchIngestRunner.Result result = matchIngestRunner.executeWithPriority(
        props.getIngestBatchSize(), priorityTier, effectivePatch);

    if (result.picked() == 0) {
      log.debug("match_queue 비어있음. {}ms 대기", props.getPollingIntervalMs());
      Thread.sleep(props.getPollingIntervalMs());
    }
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
