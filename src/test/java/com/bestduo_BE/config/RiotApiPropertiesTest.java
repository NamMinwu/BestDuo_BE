package com.bestduo_BE.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiotApiPropertiesTest {

  @Test
  void resolvedApiKeysFallsBackToSingleKeyWhenMultiKeyDisabled() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("single-key");
    properties.setApiKeys(List.of("key-a", "key-b"));
    properties.setMultiKeyEnabled(false);

    assertThat(properties.resolvedApiKeys()).containsExactly("single-key");
  }

  @Test
  void resolvedApiKeysUsesConfiguredMultiKeysWhenEnabled() {
    RiotApiProperties properties = new RiotApiProperties();
    properties.setApiKey("single-key");
    properties.setApiKeys(List.of("key-a", "key-b"));
    properties.setMultiKeyEnabled(true);

    assertThat(properties.resolvedApiKeys()).containsExactly("key-a", "key-b");
  }
}
