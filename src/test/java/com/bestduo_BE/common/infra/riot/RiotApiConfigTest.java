package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.config.RiotApiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

class RiotApiConfigTest {

  private final RiotApiConfig config = new RiotApiConfig();

  @Test
  void riotKeyPoolFallsBackToSingleKeyWhenMultiKeyDisabled() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("single-key");
    properties.setApiKeys(List.of("key-a", "key-b"));
    properties.setMultiKeyEnabled(false);

    RiotKeyPool keyPool = config.riotKeyPool(properties);

    try (KeyLease first = keyPool.lease()) {
      assertThat(first.apiKey()).isEqualTo("single-key");
    }
    try (KeyLease second = keyPool.lease()) {
      assertThat(second.apiKey()).isEqualTo("single-key");
    }
  }

  @Test
  void riotKeyPoolUsesDistinctMultiKeysWhenEnabled() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("single-key");
    properties.setApiKeys(List.of("key-a", "key-a", "key-b"));
    properties.setMultiKeyEnabled(true);

    RiotKeyPool keyPool = config.riotKeyPool(properties);

    try (KeyLease first = keyPool.lease()) {
      assertThat(first.apiKey()).isEqualTo("key-a");
    }
    try (KeyLease second = keyPool.lease()) {
      assertThat(second.apiKey()).isEqualTo("key-b");
    }
  }

  @Test
  void riotKeyPoolFallsBackToSingleKeyWhenMultiKeyListIsBlank() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("single-key");
    properties.setApiKeys(List.of(" ", ""));
    properties.setMultiKeyEnabled(true);

    RiotKeyPool keyPool = config.riotKeyPool(properties);

    try (KeyLease first = keyPool.lease()) {
      assertThat(first.apiKey()).isEqualTo("single-key");
    }
    try (KeyLease second = keyPool.lease()) {
      assertThat(second.apiKey()).isEqualTo("single-key");
    }
  }

  @Test
  void restTemplateBeansAreStillCreatedWithExistingNames() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setPlatformBaseUrl("https://kr.api.riotgames.com");
    properties.setRegionalBaseUrl("https://asia.api.riotgames.com");
    properties.setApiKey("single-key");
    RiotRateLimitInterceptor interceptor = new RiotRateLimitInterceptor(config.riotKeyPool(properties));

    RestTemplate platform = config.riotPlatformRestTemplate(new RestTemplateBuilder(), interceptor, properties);
    RestTemplate regional = config.riotRegionalRestTemplate(new RestTemplateBuilder(), interceptor, properties);

    assertThat(platform).isNotNull();
    assertThat(regional).isNotNull();
  }
}
