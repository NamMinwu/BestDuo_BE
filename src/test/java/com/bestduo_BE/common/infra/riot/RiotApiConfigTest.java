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
  @DisplayName("riotRateLimitInterceptor — 설정된 API 키를 요청 헤더에 주입한다")
  void riotRateLimitInterceptorUsesConfiguredApiKey() throws Exception {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("my-key");

    RiotRateLimitInterceptor interceptor = config.riotRateLimitInterceptor(properties);

    // 헤더 주입 확인: mock execution으로 인터셉트 실행
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
  @DisplayName("riotPlatformRestTemplate / riotRegionalRestTemplate — 빈이 정상 생성된다")
  void restTemplateBeansAreCreatedWithCorrectNames() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setPlatformBaseUrl("https://kr.api.riotgames.com");
    properties.setRegionalBaseUrl("https://asia.api.riotgames.com");
    properties.setApiKey("my-key");
    RiotRateLimitInterceptor interceptor = config.riotRateLimitInterceptor(properties);

    RestTemplate platform = config.riotPlatformRestTemplate(new RestTemplateBuilder(), interceptor, properties);
    RestTemplate regional = config.riotRegionalRestTemplate(new RestTemplateBuilder(), interceptor, properties);

    assertThat(platform).isNotNull();
    assertThat(regional).isNotNull();
  }
}
