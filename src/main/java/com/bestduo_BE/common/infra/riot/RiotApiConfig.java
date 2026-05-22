package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.config.RiotApiProperties;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Riot API 클라이언트 빈 구성.
 *
 * <p>kr.api(platform) 와 asia.api(regional) 는 Riot 측에서 별도 rate limit 버킷으로 카운팅되므로
 * (RiotRegionRateLimitProbeTest 로 검증), 인터셉터/limiter 도 호스트별로 분리한다.
 *
 * <p>한도값은 {@link RiotApiProperties.RateLimit} 에서 외부화되어 env 로 조정 가능
 * (default 90% = 18/1s + 90/2min). controlled experiment 로 95% 까지 429=0 확인됨.
 */
@Configuration
public class RiotApiConfig {

  @Bean
  public RiotRateLimitInterceptor riotPlatformRateLimitInterceptor(
      RiotApiProperties properties, RiotApiMetrics metrics) {
    return new RiotRateLimitInterceptor(
        properties.getApiKey(),
        buildLimiter(properties.getRateLimit()),
        Clock.systemUTC(),
        metrics);
  }

  @Bean
  public RiotRateLimitInterceptor riotRegionalRateLimitInterceptor(
      RiotApiProperties properties, RiotApiMetrics metrics) {
    return new RiotRateLimitInterceptor(
        properties.getApiKey(),
        buildLimiter(properties.getRateLimit()),
        Clock.systemUTC(),
        metrics);
  }

  @Bean(name = "riotPlatformRestTemplate")
  public RestTemplate riotPlatformRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      @Qualifier("riotPlatformRateLimitInterceptor")
          RiotRateLimitInterceptor rateLimitInterceptor,
      RiotApiProperties properties) {
    return buildRiotRestTemplate(restTemplateBuilder, properties.getPlatformBaseUrl(), rateLimitInterceptor);
  }

  @Bean(name = "riotRegionalRestTemplate")
  public RestTemplate riotRegionalRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      @Qualifier("riotRegionalRateLimitInterceptor")
          RiotRateLimitInterceptor rateLimitInterceptor,
      RiotApiProperties properties) {
    return buildRiotRestTemplate(restTemplateBuilder, properties.getRegionalBaseUrl(), rateLimitInterceptor);
  }

  private DualWindowRateLimiter buildLimiter(RiotApiProperties.RateLimit cfg) {
    return new DualWindowRateLimiter(
        cfg.getShortLimit(), cfg.getShortWindow(),
        cfg.getLongLimit(), cfg.getLongWindow());
  }

  private RestTemplate buildRiotRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      String baseUrl,
      RiotRateLimitInterceptor rateLimitInterceptor) {
    return restTemplateBuilder
        .rootUri(baseUrl)
        .additionalInterceptors(rateLimitInterceptor)
        .build();
  }
}
