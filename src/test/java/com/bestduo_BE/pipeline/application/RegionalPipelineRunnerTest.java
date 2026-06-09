package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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
class RegionalPipelineRunnerTest {

  @Mock private CollectMatchIdsRunner collectMatchIdsRunner;

  private PipelineProperties props;
  private RegionalPipelineRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setPollingIntervalMs(100);
    PipelineMetrics pipelineMetrics = new PipelineMetrics(new SimpleMeterRegistry());
    runner = new RegionalPipelineRunner(collectMatchIdsRunner, props, pipelineMetrics);
  }

  @Test
  @DisplayName("수집 대기 summoner가 있으면 runBatch(collect+ingest)를 호출한다")
  void executeTick_whenHasPending_runsBatch() throws InterruptedException {
    given(collectMatchIdsRunner.hasPending()).willReturn(true);

    runner.executeTick();

    verify(collectMatchIdsRunner).runBatch();
  }

  @Test
  @DisplayName("수집 대기 summoner가 없으면 폴링 간격만큼 대기한다")
  void executeTick_whenNoPending_sleepsPollingInterval() throws InterruptedException {
    given(collectMatchIdsRunner.hasPending()).willReturn(false);

    long start = System.currentTimeMillis();
    runner.executeTick();
    long elapsed = System.currentTimeMillis() - start;

    // pollingIntervalMs=100 으로 설정했으므로 최소 80ms 이상 sleep 해야 함
    assertThat(elapsed).isGreaterThanOrEqualTo(80L);
  }

  @Test
  @DisplayName("runBatch에서 429 발생 시 속도 제한 예외가 전파된다")
  void executeTick_whenRunBatchThrows429_propagates() {
    given(collectMatchIdsRunner.hasPending()).willReturn(true);
    given(collectMatchIdsRunner.runBatch()).willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }
}
