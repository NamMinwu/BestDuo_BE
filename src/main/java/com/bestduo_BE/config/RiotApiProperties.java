package com.bestduo_BE.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "external.riot")
@Validated
@Getter
@Setter
public class RiotApiProperties {

  private String platformBaseUrl;
  private String regionalBaseUrl;
  private String apiKey;

  /**
   * Riot personal/dev key 의 app-level 한도(20/1s + 100/2min) 대비 사용 비율.
   * host(kr.api / asia.api) 각각 독립 버킷으로 적용된다.
   * <p>controlled experiment (RateLimitHeadroomProbeTest) 결과 95% 까지 429=0 확인.
   * 보수적 단계 적용을 위해 default 90% 로 시작, 운영 안정 확인 후 95% 로 ramp 예정.
   */
  @Valid private RateLimit rateLimit = new RateLimit();

  @Getter
  @Setter
  public static class RateLimit {
    /** short window 요청 한도. personal key 20/1s 대비 설정값. default 18 (= 90%). */
    @Min(1)
    @Max(20)
    private int shortLimit = 18;

    /** short window 길이. default 1초. */
    @NotNull private Duration shortWindow = Duration.ofSeconds(1);

    /** long window 요청 한도. personal key 100/2min 대비 설정값. default 90 (= 90%). */
    @Min(1)
    @Max(100)
    private int longLimit = 90;

    /** long window 길이. default 2분. */
    @NotNull private Duration longWindow = Duration.ofMinutes(2);
  }
}
