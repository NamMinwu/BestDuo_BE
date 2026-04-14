package com.bestduo_BE.config;

import com.bestduo_BE.common.domain.model.Tier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파이프라인 공통 설정.
 * <p>Stage별 일일 API 예산, 배치 크기, polling 간격, tier별 matchIds 수집 수를 관리한다.
 */
@Component
@ConfigurationProperties(prefix = "pipeline")
@Getter
@Setter
public class PipelineProperties {

  /** Stage 1(SEED) 일일 API 호출 상한 */
  private int seedDailyBudget = 2000;

  /** Stage 2(COLLECT_MATCH_IDS) 일일 API 호출 상한 */
  private int collectDailyBudget = 8000;

  /** Stage 2 한 번에 처리할 summoner 수 */
  private int collectBatchSize = 20;

  /** Stage 3 한 번에 처리할 match 수 */
  private int ingestBatchSize = 10;

  /** match_queue가 빌 때 대기 시간 (ms) */
  private long pollingIntervalMs = 5000;

  /**
   * DIA/EME CoverageBucket에서 한 division 내 최대 page 수.
   * 이 수에 도달하면 다음 division으로 이동한다.
   */
  private int maxPagesPerDivision = 5;

  /** Stage 3(INGEST)에서 그날 먼저 처리할 tier. ALL_TIERS이면 기존 우선순위(CHALLENGER→…→기타) 유지. */
  private Tier stage3PriorityTier = Tier.ALL_TIERS;

  private TierMatchCount tierMatchCount = new TierMatchCount();

  @Getter
  @Setter
  public static class TierMatchCount {
    /** CHALLENGER / GRANDMASTER / MASTER 티어 summoner당 수집할 matchIds 수 */
    private int apexTiers = 30;

    /** DIAMOND / EMERALD 티어 summoner당 수집할 matchIds 수 */
    private int diamondEmerald = 10;
  }
}
