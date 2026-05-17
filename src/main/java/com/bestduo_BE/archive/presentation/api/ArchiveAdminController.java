package com.bestduo_BE.archive.presentation.api;

import com.bestduo_BE.archive.application.MatchArchiver;
import com.bestduo_BE.archive.application.MatchPayloadCleaner;
import com.bestduo_BE.common.domain.model.Tier;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cold archive 1회성 트리거.
 *
 * <p>{@code retention=3} 외 patch 의 {@code match.payload_json} 을 R2 로 옮기는 수동 진입점.
 * cron 에 영구 통합 (PR B) 되면 이 endpoint 는 제거 예정.
 *
 * <p>인증: {@code AdminApiKeyInterceptor} 가 {@code X-Admin-Key} 헤더를 검증한다.
 */
@RestController
@RequestMapping("/admin/archive")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class ArchiveAdminController {

  private static final List<Tier> DEFAULT_TIERS = List.of(
      Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER, Tier.DIAMOND, Tier.EMERALD);

  private final MatchArchiver archiver;
  private final MatchPayloadCleaner cleaner;

  @PostMapping("/match-payload")
  public ArchiveResponse archiveMatchPayload(
      @RequestParam List<String> patches,
      @RequestParam(required = false) List<Tier> tiers) {

    List<Tier> targetTiers = (tiers == null || tiers.isEmpty()) ? DEFAULT_TIERS : tiers;
    List<TierResult> results = new ArrayList<>();
    int totalArchived = 0;
    long totalBytes = 0L;

    for (String patch : patches) {
      for (Tier tier : targetTiers) {
        MatchArchiver.Result r = archiver.execute(patch, tier);
        results.add(new TierResult(patch, tier.name(),
            r.archivedCount(), r.bytesUploaded(), r.objectKey()));
        totalArchived += r.archivedCount();
        totalBytes += r.bytesUploaded();
      }
    }

    log.info("[ArchiveAdmin] complete patches={} tiers={} totalArchived={} totalBytes={}",
        patches, targetTiers, totalArchived, totalBytes);
    return new ArchiveResponse(totalArchived, totalBytes, results);
  }

  /**
   * R2 에 archive 가 완료된 (patch, tier) 의 match 행을 삭제한다.
   *
   * <p>각 (patch, tier) 마다 R2 객체 존재 (HEAD 200) 를 먼저 확인한 후 match 행을 지운다.
   * 최신 2 개 patch 는 실수 방지를 위해 자동 거부 (status="protected_latest").
   *
   * <p>archive 와 분리된 endpoint 인 이유: operator 가 R2 콘솔에서 객체 + 크기를 눈으로 확인한 뒤
   * 두 번째 단계로 호출하도록 하기 위함. 둘 다 멱등 — 같은 요청을 여러 번 보내도 안전.
   */
  @PostMapping("/cleanup-archived")
  public CleanupResponse cleanupArchived(
      @RequestParam List<String> patches,
      @RequestParam(required = false) List<Tier> tiers) {

    List<Tier> targetTiers = (tiers == null || tiers.isEmpty()) ? DEFAULT_TIERS : tiers;
    MatchPayloadCleaner.Result result = cleaner.execute(patches, targetTiers);

    log.info("[ArchiveAdmin/cleanup] complete patches={} tiers={} totalDeleted={} pairs={}",
        patches, targetTiers, result.totalDeleted(), result.results().size());
    return new CleanupResponse(result.totalDeleted(), result.results());
  }

  public record ArchiveResponse(int totalArchived, long totalBytes, List<TierResult> results) {}

  public record TierResult(String patch, String tier, int archivedCount, long bytes, String objectKey) {}

  public record CleanupResponse(int totalDeleted, List<MatchPayloadCleaner.PairResult> results) {}
}
