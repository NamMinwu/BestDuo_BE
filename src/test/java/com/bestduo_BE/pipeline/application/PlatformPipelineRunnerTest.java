package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformPipelineRunnerTest {

  @Mock private DailyLeagueEntriesRunner dailyLeagueEntriesRunner;

  private PipelineProperties props;
  private PlatformPipelineRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setPollingIntervalMs(100);
    PipelineMetrics pipelineMetrics = new PipelineMetrics(new SimpleMeterRegistry());
    runner = new PlatformPipelineRunner(dailyLeagueEntriesRunner, props, pipelineMetrics);
  }

  @Test
  @DisplayName("Stage 1 작업이 있으면 runNextChunk 호출 후 종료")
  void executeTick_whenStage1HasWork_runsStage1() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(true);

    runner.executeTick();

    verify(dailyLeagueEntriesRunner).runNextChunk();
  }

  @Test
  @DisplayName("Stage 1 작업이 없으면 polling 간격만큼 대기한다")
  void executeTick_whenNoWork_sleepsPollingInterval() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);

    long start = System.currentTimeMillis();
    runner.executeTick();
    long elapsed = System.currentTimeMillis() - start;

    // pollingIntervalMs=100 으로 설정했으므로 최소 80ms 이상 sleep 해야 함
    assertThat(elapsed).isGreaterThanOrEqualTo(80L);
    verify(dailyLeagueEntriesRunner, never()).runNextChunk();
  }

  @Test
  @DisplayName("Stage 1에서 429 발생 시 속도 제한 예외가 전파된다")
  void executeTick_whenStage1Throws429_propagates() {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(true);
    given(dailyLeagueEntriesRunner.runNextChunk()).willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }
}
