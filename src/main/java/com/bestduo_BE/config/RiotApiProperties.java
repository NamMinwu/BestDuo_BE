package com.bestduo_BE.config;

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
}
