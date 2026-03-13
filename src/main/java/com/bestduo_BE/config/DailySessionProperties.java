package com.bestduo_BE.config;

import com.bestduo_BE.domain.model.Tier;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "daily-session")
@Getter
@Setter
@Slf4j
public class DailySessionProperties {

  private boolean runSeed = true;
  private boolean runRefresh = true;
  private double seedRatio = 0.2;
  private double refreshRatio = 0.2;
  private int refreshLimit = 5;
  private Seed seed = new Seed();


  @Getter
  @Setter
  public static class Seed {
    private String queue = "RANKED_SOLO_5X5";
    private String tier = "GOLD";
    private String division = "I";
    private Tier seedTier = Tier.GOLD;
    private int startPage = 1;
    private int endPage = 1;
    private int matchesPerPuuid = 20;
  }
}
