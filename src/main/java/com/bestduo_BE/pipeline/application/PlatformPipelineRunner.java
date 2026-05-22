package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Platform host(kr.api) 전담 파이프라인 루프.
 *
 * <p>Stage 1 — {@link DailyLeagueEntriesRunner}: league-v4 기반 summoner 등록/갱신.
 *
 * <p>{@link RegionalPipelineRunner}와 독립된 가상 스레드에서 실행되며, KR limiter 의
 * 429 가 발생해도 regional 루프는 영향받지 않는다 (host 별 격리).
 *
 * <p>idle 상태 (오늘 할 work 없음) 에서는 {@code pollingIntervalMs} sleep.
 * 429 발생 시 {@link #RATE_LIMIT_SLEEP_MS} sleep 후 재시도.
 */
@Component
@ConditionalOnProperty(prefix = "pipeline.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PlatformPipelineRunner {

  private static final long RATE_LIMIT_SLEEP_MS = 60_000L;

  private final DailyLeagueEntriesRunner dailyLeagueEntriesRunner;
  private final PipelineProperties props;
  private final PipelineMetrics pipelineMetrics;

  private Thread thread;

  @PostConstruct
  public void start() {
    thread = Thread.ofVirtual().name("platform-pipeline-runner").start(this::loop);
  }

  @PreDestroy
  public void stop() {
    if (thread != null) {
      thread.interrupt();
      log.info("PlatformPipelineRunner 종료 요청 (interrupt)");
      try {
        thread.join(30_000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (thread.isAlive()) {
        log.warn("PlatformPipelineRunner 30초 내 종료되지 않음");
      }
    }
  }

  private void loop() {
    log.info("PlatformPipelineRunner 시작 (가상 스레드)");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        executeTick();
      } catch (RiotRateLimitedException e) {
        log.warn("[platform] 429 Rate limited. {}ms 대기 후 재시도", RATE_LIMIT_SLEEP_MS);
        sleep(RATE_LIMIT_SLEEP_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("PlatformPipelineRunner 종료 (interrupted)");
        break;
      } catch (Exception e) {
        log.error("PlatformPipelineRunner 예기치 않은 오류. {}ms 후 재시도",
            props.getErrorBackoffMs(), e);
        sleep(props.getErrorBackoffMs());
      }
    }
  }

  /** Stage 1 한 단위를 실행한다. 테스트에서 직접 호출 가능하도록 package-private. */
  void executeTick() throws InterruptedException {
    if (dailyLeagueEntriesRunner.hasWorkToday()) {
      log.debug("Stage 1 실행");
      try {
        dailyLeagueEntriesRunner.runNextChunk();
        pipelineMetrics.recordStageCompleted(1, "success");
      } catch (RuntimeException e) {
        pipelineMetrics.recordStageCompleted(1, "error");
        throw e;
      }
      return;
    }

    log.debug("Stage 1 할 일 없음. {}ms 대기", props.getPollingIntervalMs());
    Thread.sleep(props.getPollingIntervalMs());
  }

  private void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
