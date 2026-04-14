package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestWorker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 단일 가상 스레드 파이프라인 루프.
 *
 * <p>Stage 우선순위:
 * <ol>
 *   <li>Stage 1 — {@link DailySeedRunner}: summoner 등록/갱신 (일일 예산 소진 전)</li>
 *   <li>Stage 2 — {@link CollectMatchIdsRunner}: matchIds 수집 (일일 예산 소진 전)</li>
 *   <li>Stage 3 — {@link MatchIngestWorker}: match 상세 수집 (상시)</li>
 * </ol>
 *
 * <p>429 발생 시 {@code rateLimitSleepMs}(기본 60초) sleep 후 재시도.
 * match_queue가 비어있으면 {@code pollingIntervalMs} sleep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineRunner {

  private static final long RATE_LIMIT_SLEEP_MS = 60_000L;

  private final DailySeedRunner dailySeedRunner;
  private final CollectMatchIdsRunner collectMatchIdsRunner;
  private final MatchIngestWorker matchIngestWorker;
  private final PatchVersionService patchVersionService;
  private final PipelineProperties props;

  @PostConstruct
  public void start() {
    Thread.ofVirtual().name("pipeline-runner").start(this::loop);
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
        log.error("PipelineRunner 예기치 않은 오류. 5초 후 재시도", e);
        sleep(5_000L);
      }
    }
  }

  /**
   * Stage 1 → 2 → 3 우선순위 판단 후 한 단위를 실행한다.
   * 테스트에서 직접 호출 가능하도록 package-private.
   */
  void executeTick() throws InterruptedException {
    // Stage 1: Seed (일일 예산 내)
    if (dailySeedRunner.hasWorkToday()) {
      log.debug("Stage 1 실행");
      dailySeedRunner.runNextChunk();
      return;
    }

    // Stage 2: CollectMatchIds (일일 예산 내)
    if (collectMatchIdsRunner.hasPending()) {
      log.debug("Stage 2 실행");
      collectMatchIdsRunner.runBatch();
      return;
    }

    // Stage 3: Ingest (상시, patch+tier 우선순위)
    String currentPatch = patchVersionService.currentPatchVersion().orElse(null);
    log.debug("Stage 3 실행 (currentPatch={})", currentPatch);
    MatchIngestWorker.Result result = matchIngestWorker.executeWithPriority(
        props.getIngestBatchSize(), currentPatch);

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
