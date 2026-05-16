package com.bestduo_BE.archive.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.config.ArchiveProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * gzip 압축해 PUT. 한 (patch, tier) 의 매치 수가 수만 수준이라 메모리 부담은 수십 MB 이하.
 *
 * <p>archive 만 수행한다 — match 행 삭제는 이후 PR 에서 cron 통합 시점에 따로 처리.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class MatchArchiver {

  private static final int PAGE_SIZE = 500;
  private static final String OBJECT_KEY_PATTERN = "match-archive/patch=%s/tier=%s.jsonl.gz";

  private final MatchJpaRepository matchRepository;
  private final S3Client s3Client;
  private final ArchiveProperties props;

  public Result execute(String patch, Tier tier) {
    String objectKey = OBJECT_KEY_PATTERN.formatted(patch, tier.name());

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int archivedCount;
    try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
      archivedCount = streamPayloadsInto(patch, tier, gzip);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to gzip-encode match archive: " + objectKey, e);
    }

    if (archivedCount == 0) {
      log.info("[MatchArchiver] no matches to archive (patch={} tier={})", patch, tier);
      return new Result(0, 0L, objectKey);
    }

    byte[] body = buf.toByteArray();
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(props.getR2().getBucket())
            .key(objectKey)
            .contentType("application/gzip")
            .build(),
        RequestBody.fromBytes(body));

    log.info("[MatchArchiver] archived patch={} tier={} count={} bytes={} key={}",
        patch, tier, archivedCount, body.length, objectKey);
    return new Result(archivedCount, body.length, objectKey);
  }

  private int streamPayloadsInto(String patch, Tier tier, GZIPOutputStream gzip) throws IOException {
    String cursor = "";
    int total = 0;
    while (true) {
      List<Match> page =
          matchRepository.findPageByTierAndPatch(tier.name(), patch, cursor, PAGE_SIZE);
      if (page.isEmpty()) {
        break;
      }
      for (Match m : page) {
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
