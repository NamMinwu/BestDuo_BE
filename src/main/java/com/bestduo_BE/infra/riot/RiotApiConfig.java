package com.bestduo_BE.infra.riot;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RiotApiConfig {

  @Bean(name = "riotPlatformRestTemplate")
  public RestTemplate riotPlatformRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${external.riot.platform-base-url}") String platformBaseUrl,
      @Value("${external.riot.api-key}") String apiKey) {
    return buildRiotRestTemplate(restTemplateBuilder, platformBaseUrl, apiKey);
  }

  @Bean(name = "riotRegionalRestTemplate")
  public RestTemplate riotRegionalRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${external.riot.regional-base-url}") String regionalBaseUrl,
      @Value("${external.riot.api-key}") String apiKey) {
    return buildRiotRestTemplate(restTemplateBuilder, regionalBaseUrl, apiKey);
  }

  private RestTemplate buildRiotRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      String baseUrl,
      String apiKey) {
    return restTemplateBuilder
        .rootUri(baseUrl)
        .additionalInterceptors((request, body, execution) -> {
          request.getHeaders().add("X-Riot-Token", apiKey);
          return execution.execute(request, body);
        })
        .build();
  }
}
