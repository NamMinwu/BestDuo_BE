package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestRunner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Regional host(asia.api) 전담 파이프라인 루프.
 *
 * <p>Stage 우선순위:
 * <ol>
 *   <li>Stage 2 — {@link CollectMatchIdsRunner}: matchIds 수집 (일일 예산 내)</li>
 *   <li>Stage 3 — {@link MatchIngestRunner}: match 상세 수집 (상시, patch+tier 우선순위)</li>
 * </ol>
 *
 * <p>{@link PlatformPipelineRunner}와 독립된 가상 스레드에서 실행되며, ASIA limiter 의
 * 429 가 발생해도 platform 루프는 영향받지 않는다 (host 별 격리).
 *
 * <p>match_queue 가 비어있으면 {@code pollingIntervalMs} sleep.
 * 429 발생 시 {@link #RATE_LIMIT_SLEEP_MS} sleep 후 재시도.
 */
@Component
@ConditionalOnProperty(prefix = "pipeline.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RegionalPipelineRunner {

  private static final long RATE_LIMIT_SLEEP_MS = 60_000L;
  private static final Tier ROUND_ROBIN_LOWEST_TIER = Tier.EMERALD;

  private final CollectMatchIdsRunner collectMatchIdsRunner;
  private final MatchIngestRunner matchIngestRunner;
  private final PatchVersionService patchVersionService;
  private final PipelineProperties props;
  private final PipelineMetrics pipelineMetrics;

  private Thread thread;

  @PostConstruct
  public void start() {
    thread = Thread.ofVirtual().name("regional-pipeline-runner").start(this::loop);
  }

  @PreDestroy
  public void stop() {
    if (thread != null) {
      thread.interrupt();
      log.info("RegionalPipelineRunner 종료 요청 (interrupt)");
      try {
        thread.join(30_000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (thread.isAlive()) {
        log.warn("RegionalPipelineRunner 30초 내 종료되지 않음");
      }
    }
  }

  private void loop() {
    log.info("RegionalPipelineRunner 시작 (가상 스레드)");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        executeTick();
      } catch (RiotRateLimitedException e) {
        log.warn("[regional] 429 Rate limited. {}ms 대기 후 재시도", RATE_LIMIT_SLEEP_MS);
        sleep(RATE_LIMIT_SLEEP_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("RegionalPipelineRunner 종료 (interrupted)");
        break;
      } catch (Exception e) {
        log.error("RegionalPipelineRunner 예기치 않은 오류. {}ms 후 재시도",
            props.getErrorBackoffMs(), e);
        sleep(props.getErrorBackoffMs());
      }
    }
  }

  /** Stage 2 → 3 우선순위 판단 후 한 단위를 실행한다. 테스트에서 직접 호출 가능하도록 package-private. */
  void executeTick() throws InterruptedException {
    // Stage 2: CollectMatchIds (일일 예산 내)
    if (collectMatchIdsRunner.hasPending()) {
      log.debug("Stage 2 실행");
      try {
        collectMatchIdsRunner.runBatch();
        pipelineMetrics.recordStageCompleted(2, "success");
      } catch (RuntimeException e) {
        pipelineMetrics.recordStageCompleted(2, "error");
        throw e;
      }
      return;
    }

    // Stage 3: Ingest (상시, patch+tier 우선순위)
    // Stage 2가 유효 패치 기준으로 match ID를 수집하므로, Stage 3도 같은 기준을 사용해야 우선순위가 일치한다.
    String effectivePatch = patchVersionService.resolveEffectivePatchContext()
        .map(EffectivePatchContext::patch)
        .orElse(null);
    Tier priorityTier = props.getStage3PriorityTier();
    log.debug("Stage 3 실행 (effectivePatch={}, priorityTier={})", effectivePatch, priorityTier);
    MatchIngestRunner.Result result;
    try {
      result = runStage3WithTierRoundRobin(priorityTier, effectivePatch);
      pipelineMetrics.recordStageCompleted(3, "success");
    } catch (RuntimeException e) {
      pipelineMetrics.recordStageCompleted(3, "error");
      throw e;
    }

    if (result.picked() == 0) {
      log.debug("match_queue 비어있음. {}ms 대기", props.getPollingIntervalMs());
      Thread.sleep(props.getPollingIntervalMs());
    }
  }

  /**
   * priorityTier가 지정되지 않으면(null/ALL_TIERS) tier 필터 없이 한 번 호출한다(레거시 동작).
   * 지정되면 priority → 나머지 티어 순으로 순회하다가 picked > 0 시점에 멈춘다.
   * 순회 범위는 CHALLENGER ~ {@link #ROUND_ROBIN_LOWEST_TIER}까지로 제한된다.
   * 모든 티어가 비면 마지막 Result(picked=0)를 반환해 호출부가 sleep으로 진입하도록 한다.
   */
  private MatchIngestRunner.Result runStage3WithTierRoundRobin(
      Tier priorityTier, String effectivePatch) {
    int batchSize = props.getIngestBatchSize();
    if (priorityTier == null || priorityTier == Tier.ALL_TIERS) {
      return matchIngestRunner.executeWithPriority(batchSize, null, effectivePatch);
    }

    List<Tier> order = buildTierOrder(priorityTier);
    MatchIngestRunner.Result result = null;
    for (Tier t : order) {
      result = matchIngestRunner.executeWithPriority(batchSize, t, effectivePatch);
      if (result.picked() > 0) {
        return result;
      }
      log.debug("Stage 3 tier={} 비어있음, 다음 tier로 순회", t);
    }
    return result;
  }

  private static List<Tier> buildTierOrder(Tier priority) {
    List<Tier> allowed = Arrays.stream(Tier.values())
        .filter(t -> t != Tier.ALL_TIERS)
        .filter(t -> t.ordinal() <= ROUND_ROBIN_LOWEST_TIER.ordinal())
        .toList();
    List<Tier> ordered = new ArrayList<>(allowed.size() + 1);
    if (allowed.contains(priority)) {
      ordered.add(priority);
    }
    for (Tier t : allowed) {
      if (t != priority) {
        ordered.add(t);
      }
    }
    return ordered;
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
