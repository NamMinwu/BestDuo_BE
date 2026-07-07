package com.bestduo_BE.archive.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.projection.MatchPayloadProjection;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.config.ArchiveProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * (patch, tier) 단위로 {@code match.payload_json} 들을 R2 (S3-compatible) 로 archive 한다.
 *
 * <p>keyset pagination 으로 stream 하면서 jsonl 한 줄에 한 payload 를 적고 전체를 단일 객체로
 * gzip 압축해 PUT.
 *
 * <p>메모리 관리:
 * <ul>
 *   <li>{@link MatchPayloadProjection} 으로 fetch 해서 Hibernate L1 캐시 우회 — 페이지가 GC 대상이 됨
 *   <li>gzip 출력은 temp 파일로 흘려보내고 {@code RequestBody.fromFile} 로 업로드 — 압축본을 메모리에 통째로 안 올림
 * </ul>
 *
 * <p>archive 만 수행한다 — match 행 삭제는 cleanup endpoint 가 따로 처리.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class MatchArchiver {

  private static final int PAGE_SIZE = 500;
  private static final String OBJECT_KEY_PATTERN = "match-archive/patch=%s/tier=%s.jsonl.gz";
  private static final String TEMP_FILE_PREFIX = "match-archive-";
  private static final String TEMP_FILE_SUFFIX = ".jsonl.gz";

  private final MatchJpaRepository matchRepository;
  private final S3Client s3Client;
  private final ArchiveProperties props;

  public Result execute(String patch, Tier tier) {
    String objectKey = OBJECT_KEY_PATTERN.formatted(patch, tier.name());

    Path tempFile;
    try {
      tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create archive temp file: " + objectKey, e);
    }

    try {
      int archivedCount;
      try (OutputStream out = Files.newOutputStream(tempFile);
          GZIPOutputStream gzip = new GZIPOutputStream(out)) {
        archivedCount = streamPayloadsInto(patch, tier, gzip);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to gzip-encode match archive: " + objectKey, e);
      }

      if (archivedCount == 0) {
        log.info("[MatchArchiver] no matches to archive (patch={} tier={})", patch, tier);
        return new Result(0, 0L, objectKey);
      }

      long bytesUploaded;
      try {
        bytesUploaded = Files.size(tempFile);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read archive temp file size: " + objectKey, e);
      }

      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(props.getR2().getBucket())
              .key(objectKey)
              .contentType("application/gzip")
              .build(),
          RequestBody.fromFile(tempFile));

      log.info("[MatchArchiver] archived patch={} tier={} count={} bytes={} key={}",
          patch, tier, archivedCount, bytesUploaded, objectKey);
      return new Result(archivedCount, bytesUploaded, objectKey);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        log.warn("[MatchArchiver] failed to delete temp file: {}", tempFile, e);
      }
    }
  }

  private int streamPayloadsInto(String patch, Tier tier, GZIPOutputStream gzip) throws IOException {
    String cursor = "";
    int total = 0;
    while (true) {
      List<MatchPayloadProjection> page =
          matchRepository.findPayloadPageByTierAndPatch(tier.name(), patch, cursor, PAGE_SIZE);
      if (page.isEmpty()) {
        break;
      }
      for (MatchPayloadProjection m : page) {
        gzip.write(m.getPayloadJson().getBytes(StandardCharsets.UTF_8));
        gzip.write('\n');
        cursor = m.getMatchId();
        total++;
      }
    }
    return total;
  }

  public record Result(int archivedCount, long bytesUploaded, String objectKey) {}
}
