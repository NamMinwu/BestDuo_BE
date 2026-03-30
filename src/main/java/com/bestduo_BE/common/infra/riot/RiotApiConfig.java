package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.config.RiotApiProperties;
import java.time.Clock;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RiotApiConfig {

  @Bean
  public RiotKeyPool riotKeyPool(RiotApiProperties properties) {
    return RiotKeyPool.fromKeys(properties.resolvedApiKeys(), Clock.systemUTC());
  }

  @Bean
  public RiotRateLimitInterceptor riotRateLimitInterceptor(RiotKeyPool keyPool) {
    return new RiotRateLimitInterceptor(keyPool);
  }

  @Bean(name = "riotPlatformRestTemplate")
  public RestTemplate riotPlatformRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RiotRateLimitInterceptor rateLimitInterceptor,
      RiotApiProperties properties) {
    return buildRiotRestTemplate(restTemplateBuilder, properties.getPlatformBaseUrl(), rateLimitInterceptor);
  }

  @Bean(name = "riotRegionalRestTemplate")
  public RestTemplate riotRegionalRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RiotRateLimitInterceptor rateLimitInterceptor,
      RiotApiProperties properties) {
    return buildRiotRestTemplate(restTemplateBuilder, properties.getRegionalBaseUrl(), rateLimitInterceptor);
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
