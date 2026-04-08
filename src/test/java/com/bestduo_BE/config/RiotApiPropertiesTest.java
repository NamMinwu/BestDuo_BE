package com.bestduo_BE.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiotApiPropertiesTest {

  @Test
  void apiKeyIsReturnedAsConfigured() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("my-key");

    assertThat(properties.getApiKey()).isEqualTo("my-key");
  }
}
