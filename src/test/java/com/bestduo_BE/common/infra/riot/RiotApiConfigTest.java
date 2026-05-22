package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.config.RiotApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

class RiotApiConfigTest {

  private final RiotApiConfig config = new RiotApiConfig();

  @Test
  @DisplayName("riotPlatformRateLimitInterceptor — 설정된 API 키를 요청 헤더에 주입한다")
  void platformInterceptorUsesConfiguredApiKey() throws Exception {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("my-key");

    RiotRateLimitInterceptor interceptor = config.riotPlatformRateLimitInterceptor(properties);

    var request = new org.springframework.mock.http.client.MockClientHttpRequest();
    request.setURI(java.net.URI.create("https://kr.api.riotgames.com/test"));
    var execution = org.mockito.Mockito.mock(
        org.springframework.http.client.ClientHttpRequestExecution.class);
    org.mockito.Mockito.when(execution.execute(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new org.springframework.mock.http.client.MockClientHttpResponse(
            new byte[0], org.springframework.http.HttpStatus.OK));

    interceptor.intercept(request, new byte[0], execution);

    assertThat(request.getHeaders().getFirst("X-Riot-Token")).isEqualTo("my-key");
  }

  @Test
  @DisplayName("platform / regional 인터셉터 — 호스트별 독립 인스턴스로 생성된다")
  void platformAndRegionalInterceptorsAreSeparateInstances() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("my-key");

    RiotRateLimitInterceptor platform = config.riotPlatformRateLimitInterceptor(properties);
    RiotRateLimitInterceptor regional = config.riotRegionalRateLimitInterceptor(properties);

    // 호스트별 rate limit 버킷 독립을 위해 인스턴스가 분리되어야 함
    assertThat(platform).isNotSameAs(regional);
  }

  @Test
  @DisplayName("riotPlatformRestTemplate / riotRegionalRestTemplate — 빈이 정상 생성된다")
  void restTemplateBeansAreCreatedWithCorrectNames() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setPlatformBaseUrl("https://kr.api.riotgames.com");
    properties.setRegionalBaseUrl("https://asia.api.riotgames.com");
    properties.setApiKey("my-key");

    RiotRateLimitInterceptor platformInterceptor =
        config.riotPlatformRateLimitInterceptor(properties);
    RiotRateLimitInterceptor regionalInterceptor =
        config.riotRegionalRateLimitInterceptor(properties);

    RestTemplate platform =
        config.riotPlatformRestTemplate(new RestTemplateBuilder(), platformInterceptor, properties);
    RestTemplate regional =
        config.riotRegionalRestTemplate(new RestTemplateBuilder(), regionalInterceptor, properties);

    assertThat(platform).isNotNull();
    assertThat(regional).isNotNull();
  }
}
