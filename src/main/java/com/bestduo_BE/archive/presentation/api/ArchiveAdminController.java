package com.bestduo_BE.archive.presentation.api;

import com.bestduo_BE.archive.application.MatchArchiver;
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

  public record ArchiveResponse(int totalArchived, long totalBytes, List<TierResult> results) {}

  public record TierResult(String patch, String tier, int archivedCount, long bytes, String objectKey) {}
}
