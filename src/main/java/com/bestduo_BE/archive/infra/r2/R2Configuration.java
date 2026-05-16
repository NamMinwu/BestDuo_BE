package com.bestduo_BE.archive.infra.r2;

import com.bestduo_BE.config.ArchiveProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Cloudflare R2 (S3-compatible) 용 {@link S3Client} bean 등록.
 *
 * <p>{@code archive.r2.enabled=true} 일 때만 등록된다 — 로컬·테스트 환경에서 외부 의존성
 * 없이 앱이 뜨도록 하기 위한 안전장치.
 *
 * <p>R2 는 region 개념이 없으므로 {@code Region.of("auto")} 를 사용한다.
 * Path-style 접근으로 endpoint 가 {@code https://<account-id>.r2.cloudflarestorage.com}
 * 형태에 안전하게 동작한다.
 */
@Configuration
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@RequiredArgsConstructor
public class R2Configuration {

  private final ArchiveProperties props;

  @Bean
  public S3Client r2S3Client() {
    ArchiveProperties.R2 r2 = props.getR2();
    return S3Client.builder()
        .endpointOverride(URI.create(r2.getEndpoint()))
        .region(Region.of("auto"))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(r2.getAccessKeyId(), r2.getSecretAccessKey())))
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build())
        .build();
  }
}
