package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.infra.persistence.repository.IngestQueueStatsJpaRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MatchQueueGaugeRegistrar {

  private final PipelineMetrics pipelineMetrics;
  private final IngestQueueStatsJpaRepository statsRepository;

  public MatchQueueGaugeRegistrar(
      PipelineMetrics pipelineMetrics,
      IngestQueueStatsJpaRepository statsRepository) {
    this.pipelineMetrics = pipelineMetrics;
    this.statsRepository = statsRepository;
  }

  @PostConstruct
  public void register() {
    pipelineMetrics.registerMatchQueueGauge(() -> statsRepository.countReadyByTier(null));
  }
}
