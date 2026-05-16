package com.bestduo_BE.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Aggregate 모듈 설정.
 * <p>retention: cron에서 보존할 최근 patch 개수. 이 수 외의 patch 통계는 자동 정리된다.
 * <p>범위를 벗어난 env 주입 시 앱 시작이 거부된다 (운영 안전장치).
 */
@Component
@ConfigurationProperties(prefix = "aggregate")
@Validated
@Getter
@Setter
public class AggregateProperties {

  @Valid private Retention retention = new Retention();

  @Getter
  @Setter
  public static class Retention {
    /**
     * 보존할 최근 patch 개수.
     * <p>예: 3이면 가장 최근 3개 patch의 stat/matchup/raw 데이터만 유지하고 나머지는 삭제.
     * <p>grace period 정책과 충돌 가능성을 줄이려면 최소 3 권장.
     */
    @Min(1)
    @Max(10)
    private int patches = 3;
  }
}
