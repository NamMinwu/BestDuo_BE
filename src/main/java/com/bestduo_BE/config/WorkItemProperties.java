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

  private boolean workerEnabled = false;
  private int poolSize = 4;
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
    private long unverifiedBacklog = 5L;
    private long minSeedTrigger = 3L;
    private int ingestWindowMinutes = 30;
  }

  @Getter
  @Setter
  public static class Batch {
    private int verify = 10;
    private int refresh = 10;
    private int ingest = 20;
    private int seed = 1;
  }
}
