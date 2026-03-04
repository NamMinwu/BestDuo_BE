package com.bestduo_BE.infra.riot;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RiotApiConfig {

  @Bean
  public DualWindowRateLimiter riotRateLimiter() {
    return new DualWindowRateLimiter(
        20, Duration.ofSeconds(1),
        100, Duration.ofMinutes(2)
    );
  }

  @Bean
  public RiotRateLimitInterceptor riotRateLimitInterceptor(DualWindowRateLimiter limiter) {
    return new RiotRateLimitInterceptor(limiter);
  }

  @Bean(name = "riotPlatformRestTemplate")
  public RestTemplate riotPlatformRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RiotRateLimitInterceptor rateLimitInterceptor,
      @Value("${external.riot.platform-base-url}") String platformBaseUrl,
      @Value("${external.riot.api-key}") String apiKey) {
    return buildRiotRestTemplate(restTemplateBuilder, platformBaseUrl, apiKey, rateLimitInterceptor);
  }

  @Bean(name = "riotRegionalRestTemplate")
  public RestTemplate riotRegionalRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RiotRateLimitInterceptor rateLimitInterceptor,
      @Value("${external.riot.regional-base-url}") String regionalBaseUrl,
      @Value("${external.riot.api-key}") String apiKey) {
    return buildRiotRestTemplate(restTemplateBuilder, regionalBaseUrl, apiKey, rateLimitInterceptor);
  }

  private RestTemplate buildRiotRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      String baseUrl,
      String apiKey,
      RiotRateLimitInterceptor rateLimitInterceptor) {

    ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
      request.getHeaders().add("X-Riot-Token", apiKey);
      return execution.execute(request, body);
    };

    return restTemplateBuilder
        .rootUri(baseUrl)
        // ✅ 여기서 List.of 쓰지 말고 varargs로!
        .additionalInterceptors(rateLimitInterceptor, authInterceptor)
        .build();
  }
}