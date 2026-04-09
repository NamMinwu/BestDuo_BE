package com.bestduo_BE.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "work-item")
@Getter
@Setter
public class WorkItemProperties {

  private boolean workerEnabled = true;
  private long pollingIntervalMs = 500L;
  private long schedulerFixedDelayMs = 30_000L;
  private int staleMinutes = 10;
  private int duplicatePendingLimit = 1;

  private final Threshold threshold = new Threshold();
  private final Batch batch = new Batch();

  @Getter
  @Setter
  public static class Threshold {
    private long verifiedPool = 10L;
    private long readyMatchQueue = 20L;
    private long recentIngest = 5L;
    private long minSeedTrigger = 3L;
    private int ingestWindowMinutes = 30;
    /** REFRESH에서 league-v4 tier 재조회를 생략할 tierObservedAt 캐시 유효 시간 (시간 단위) */
    private int tierCacheTtlHours = 12;
  }

  @Getter
  @Setter
  public static class Batch {
    private int refresh = 10;
    private int ingest = 20;
    private int seed = 1;
    /** SEED에서 한 division을 소진 처리할 최대 페이지 수 (초과 시 다음 division으로 rotation) */
    private int seedMaxPagesPerDivision = 10;
  }
}
