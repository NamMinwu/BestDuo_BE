package com.bestduo_BE.config;

import com.bestduo_BE.common.domain.model.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 파이프라인 공통 설정.
 * <p>Stage 2 일일 API 예산, 배치 크기, polling 간격, tier별 matchIds 수집 수를 관리한다.
 * <p>Stage 1은 platform(kr.api) 전용 limiter 의 자연 throttle 에 의존하므로 별도 일일 cap이 없다.
 * <p>범위를 벗어난 env 주입 시 앱 시작이 거부된다 (운영 안전장치).
 */
@Component
@ConfigurationProperties(prefix = "pipeline")
@Validated
@Getter
@Setter
public class PipelineProperties {

  /** Stage 2(COLLECT_MATCH_IDS) 일일 API 호출 상한 */
  @Min(1)
  @Max(50_000)
  private int collectDailyBudget = 8000;

  /** Stage 2 한 번에 처리할 summoner 수 */
  @Min(1)
  @Max(500)
  private int collectBatchSize = 20;

  /** Stage 3 한 번에 처리할 match 수 */
  @Min(1)
  @Max(500)
  private int ingestBatchSize = 10;

  /** match_queue가 빌 때 대기 시간 (ms) */
  @Min(100)
  @Max(600_000)
  private long pollingIntervalMs = 5000;

  /** 예기치 않은 오류 발생 시 재시도 전 대기 시간 (ms) */
  @Min(100)
  @Max(600_000)
  private long errorBackoffMs = 5000;

  /**
   * DIA/EME CoverageBucket에서 한 division 내 최대 page 수.
   * 이 수에 도달하면 다음 division으로 이동한다.
   */
  @Min(1)
  @Max(10_000)
  private int maxPagesPerDivision = 5;

  /**
   * 자동 생성되는 DIA/EME CoverageBucket의 목표 매치 수.
   * 버킷 status(COLLECTING/SUFFICIENT) 판단 기준으로만 사용된다.
   */
  @Min(1)
  private long diaEmeCoverageTarget = 500_000L;

  /** DIA/EME 버킷당 하루에 처리할 최대 페이지 수 (per-bucket). */
  @Min(1)
  @Max(10_000)
  private int diaEmeDailyPageQuota = 10;

  /** Stage 3(INGEST)에서 그날 먼저 처리할 tier. null이면 기존 우선순위(CHALLENGER→…→기타) 유지. */
  private Tier stage3PriorityTier = null;

  @Valid private Ingest ingest = new Ingest();

  @Valid private TierMatchCount tierMatchCount = new TierMatchCount();

  @Getter
  @Setter
  public static class Ingest {
    /** RUNNING 상태가 이 분 이상 지속되면 stale로 복구 */
    @Min(1)
    @Max(1440)
    private int staleMinutes = 10;

    /** ERROR 상태 재시도 쿨다운 (분) */
    @Min(1)
    @Max(1440)
    private int errorCooldownMinutes = 10;

    /** 최대 재시도 횟수 */
    @Min(0)
    @Max(100)
    private int maxRetry = 2;
  }

  @Getter
  @Setter
  public static class TierMatchCount {
    /** CHALLENGER / GRANDMASTER / MASTER 티어 summoner당 수집할 matchIds 수 */
    @Min(1)
    @Max(100)
    private int apexTiers = 30;

    /** DIAMOND / EMERALD 티어 summoner당 수집할 matchIds 수 */
    @Min(1)
    @Max(100)
    private int diamondEmerald = 10;
  }
}
