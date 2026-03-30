package com.bestduo_BE.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "external.riot")
@Getter
@Setter
public class RiotApiProperties {

  private String platformBaseUrl;
  private String regionalBaseUrl;
  private String apiKey;
  private List<String> apiKeys = List.of();
  private boolean multiKeyEnabled = false;

  public List<String> resolvedApiKeys() {
    List<String> sanitizedKeys = apiKeys == null ? List.of() : apiKeys.stream()
          .filter(key -> key != null && !key.isBlank())
          .map(String::trim)
          .distinct()
          .toList();

    if (multiKeyEnabled && !sanitizedKeys.isEmpty()) {
      return sanitizedKeys;
    }

    if (apiKey == null || apiKey.isBlank()) {
      return List.of();
    }

    return List.of(apiKey.trim());
  }
}
