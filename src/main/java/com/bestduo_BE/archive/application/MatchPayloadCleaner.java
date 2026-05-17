package com.bestduo_BE.archive.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.config.ArchiveProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * R2 에 archive 가 완료된 (patch, tier) 의 match 행을 디스크에서 회수한다.
 *
 * <p>판정 기준: 개수가 아니라 R2 에 {@code match-archive/patch=X/tier=Y.jsonl.gz} 객체가 실제로
 * 존재하는지 (HEAD 200). 그래야 이전 실행에서 archive 만 끝나고 DELETE 가 실패한 경우의 재시도가 안전하다.
 *
 * <p>가드: 최신 2 개 patch 는 operator 가 실수로 지정해도 거부한다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class MatchPayloadCleaner {

  private static final int PROTECTED_LATEST_COUNT = 2;
  private static final String OBJECT_KEY_PATTERN = "match-archive/patch=%s/tier=%s.jsonl.gz";
  private static final Pattern PATCH_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

  private final MatchJpaRepository matchRepository;
  private final S3Client s3Client;
  private final ArchiveProperties props;

  public Result execute(List<String> patches, List<Tier> tiers) {
    Set<String> protectedPatches = computeProtectedLatest();
    List<PairResult> results = new ArrayList<>();
    int totalDeleted = 0;

    for (String patch : patches) {
      if (protectedPatches.contains(patch)) {
        for (Tier tier : tiers) {
          results.add(new PairResult(patch, tier.name(), "protected_latest", 0L, 0));
        }
        continue;
      }
      for (Tier tier : tiers) {
        results.add(processPair(patch, tier));
      }
    }

    for (PairResult r : results) {
      totalDeleted += r.deletedRows();
    }

    log.info("[MatchPayloadCleaner] protected={} totalDeleted={} pairs={}",
        protectedPatches, totalDeleted, results.size());
    return new Result(totalDeleted, results);
  }

  private PairResult processPair(String patch, Tier tier) {
    String key = OBJECT_KEY_PATTERN.formatted(patch, tier.name());

    long objectBytes;
    try {
      HeadObjectResponse head = s3Client.headObject(
          HeadObjectRequest.builder().bucket(props.getR2().getBucket()).key(key).build());
      objectBytes = head.contentLength() == null ? 0L : head.contentLength();
    } catch (NoSuchKeyException nfe) {
      return new PairResult(patch, tier.name(), "no_archive_found", 0L, 0);
    } catch (RuntimeException ex) {
      log.warn("[MatchPayloadCleaner] head failed key={} err={}", key, ex.toString());
      return new PairResult(patch, tier.name(), "head_failed", 0L, 0);
    }

    int deleted;
    try {
      deleted = matchRepository.deleteByTierAndPatch(tier.name(), patch);
    } catch (RuntimeException ex) {
      log.error("[MatchPayloadCleaner] delete failed tier={} patch={}", tier, patch, ex);
      return new PairResult(patch, tier.name(), "delete_failed", objectBytes, 0);
    }

    log.info("[MatchPayloadCleaner] ok tier={} patch={} bytes={} deleted={}",
        tier, patch, objectBytes, deleted);
    return new PairResult(patch, tier.name(), "ok", objectBytes, deleted);
  }

  private Set<String> computeProtectedLatest() {
    return matchRepository.findDistinctPatches().stream()
        .filter(MatchPayloadCleaner::isParseable)
        .sorted(Comparator
            .comparingInt(MatchPayloadCleaner::major)
            .thenComparingInt(MatchPayloadCleaner::minor)
            .reversed())
        .limit(PROTECTED_LATEST_COUNT)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static boolean isParseable(String patch) {
    return patch != null && PATCH_PATTERN.matcher(patch).matches();
  }

  private static int major(String patch) {
    return parseGroup(patch, 1);
  }

  private static int minor(String patch) {
    return parseGroup(patch, 2);
  }

  private static int parseGroup(String patch, int group) {
    Matcher m = PATCH_PATTERN.matcher(patch);
    if (!m.matches()) {
      throw new IllegalArgumentException("Unparseable patch: " + patch);
    }
    return Integer.parseInt(m.group(group));
  }

  public record Result(int totalDeleted, List<PairResult> results) {}

  public record PairResult(
      String patch,
      String tier,
      String status,
      long objectBytes,
      int deletedRows) {}
}
