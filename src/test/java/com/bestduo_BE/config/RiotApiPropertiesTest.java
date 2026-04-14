package com.bestduo_BE.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiotApiPropertiesTest {

  @Test
  @DisplayName("설정된 API 키가 그대로 반환된다")
  void apiKeyIsReturnedAsConfigured() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("my-key");

    assertThat(properties.getApiKey()).isEqualTo("my-key");
  }
}
