package com.bestduo_BE.config.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Admin API 인증용 키 설정. `admin.api.key` env/yml로 주입하며, 비어 있으면 앱 시작이 거부된다.
 */
@ConfigurationProperties(prefix = "admin.api")
@Validated
@Getter
@Setter
public class AdminApiKeyProperties {

  @NotBlank
  private String key;
}
