package com.bestduo_BE.debug.application;

import com.bestduo_BE.config.ArchiveProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Railway volume 의 {@code /dumps/*.hprof} 파일을 R2 로 업로드한다.
 *
 * <p>운영 흐름: JVM 의 {@code -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps} 가 OOM
 * 발생 시 자동으로 hprof 파일을 만들고, 앱이 재시작된 뒤 operator 가 이 uploader 를 호출해
 * hprof 를 R2 로 옮겨 로컬에서 분석 가능하게 한다.
 *
 * <p>archive R2 인프라 (S3Client + bucket) 를 그대로 재사용 — debug 전용 별도 bucket 분리는
 * 일회성 디버그 도구라 over-engineering.
 */
@Service
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class HeapDumpUploader {

  private static final String R2_KEY_PREFIX = "debug/";
  private static final String HPROF_SUFFIX = ".hprof";

  private final Path dumpDir;
  private final S3Client s3Client;
  private final ArchiveProperties props;

  public HeapDumpUploader(
      @Value("${debug.heap-dump.dir:/dumps}") String dumpDir,
      S3Client s3Client,
      ArchiveProperties props) {
    this.dumpDir = Path.of(dumpDir);
    this.s3Client = s3Client;
    this.props = props;
  }

  public List<UploadResult> uploadAllHprofFiles() throws IOException {
    if (!Files.isDirectory(dumpDir)) {
      log.warn("[HeapDumpUploader] dump dir not found: {}", dumpDir);
      return List.of();
    }

    List<Path> hprofs;
    try (Stream<Path> files = Files.list(dumpDir)) {
      hprofs = files
          .filter(p -> p.getFileName().toString().endsWith(HPROF_SUFFIX))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .toList();
    }

    List<UploadResult> results = new ArrayList<>();
    for (Path file : hprofs) {
      String key = R2_KEY_PREFIX + file.getFileName().toString();
      long bytes = Files.size(file);
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(props.getR2().getBucket())
              .key(key)
              .contentType("application/octet-stream")
              .build(),
          RequestBody.fromFile(file));
      log.info("[HeapDumpUploader] uploaded {} ({} bytes) -> {}", file, bytes, key);
      results.add(new UploadResult(file.toString(), key, bytes));
    }
    return results;
  }

  public record UploadResult(String localPath, String r2Key, long bytes) {}
}
