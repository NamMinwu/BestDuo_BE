package com.bestduo_BE.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Cold archive 모듈 설정.
 * <p>retention=3 외 patch 의 {@code match.payload_json} 을 R2 (S3-compatible) 로
 * 옮기기 위한 자격증명/엔드포인트.
 * <p>{@code archive.r2.enabled=false} 일 때 S3Client bean 은 등록되지 않는다 — 로컬·테스트
 * 환경에서 외부 의존성 없이 앱이 뜨도록 하기 위함.
 */
@Component
@ConfigurationProperties(prefix = "archive")
@Validated
@Getter
@Setter
public class ArchiveProperties {

  @Valid private R2 r2 = new R2();

  @Getter
  @Setter
  public static class R2 {
    /** R2 통합 활성화 스위치. false 면 S3Client bean 등록 안 함. */
    private boolean enabled = false;

    /** R2 S3-compatible endpoint. 예: https://&lt;account-id&gt;.r2.cloudflarestorage.com */
    private String endpoint = "";

    /** R2 dashboard 에서 발급한 access key id. */
    private String accessKeyId = "";

    /** R2 dashboard 에서 발급한 secret access key. */
    private String secretAccessKey = "";

    /** archive 대상 bucket 이름. */
    private String bucket = "";
  }
}
