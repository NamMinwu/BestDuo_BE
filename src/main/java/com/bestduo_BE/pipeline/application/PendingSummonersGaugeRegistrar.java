package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * {@code pipeline.collect.pending_summoners} gauge 등록.
 * matchIds 수집 대기 중인 summoner 수를 노출해 "수집 백로그"를 관측한다
 * (match_queue 제거 후 큐-깊이 지표를 대체).
 */
@Component
public class PendingSummonersGaugeRegistrar {

  private final PipelineMetrics pipelineMetrics;
  private final SummonerJpaRepository summonerRepository;

  public PendingSummonersGaugeRegistrar(
      PipelineMetrics pipelineMetrics, SummonerJpaRepository summonerRepository) {
    this.pipelineMetrics = pipelineMetrics;
    this.summonerRepository = summonerRepository;
  }

  @PostConstruct
  public void register() {
    pipelineMetrics.registerPendingSummonersGauge(
        summonerRepository::countMatchIdsPendingSummoners);
  }
}
