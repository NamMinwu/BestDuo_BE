package com.bestduo_BE.common.infra.champion;

import com.bestduo_BE.common.application.PatchVersionService;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 6시간마다 Data Dragon versions.json을 폴링하여 새 패치를 자동 감지하고 등록한다.
 *
 * <p>패치 주기가 약 2주이므로 6시간 간격은 충분히 빠른 감지 주기이다.
 * {@code releasedAt}은 최초 감지 시점을 근사값으로 사용한다.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "patch-sync.enabled", havingValue = "true", matchIfMissing = true)
public class DataDragonPatchSyncScheduler {

  private static final String VERSIONS_URL =
      "https://ddragon.leagueoflegends.com/api/versions.json";

  private final RestTemplate restTemplate;
  private final PatchVersionService patchVersionService;

  /** Spring이 사용하는 기본 생성자 — RestTemplate을 직접 생성한다. */
  @Autowired
  public DataDragonPatchSyncScheduler(PatchVersionService patchVersionService) {
    this(new RestTemplate(), patchVersionService);
  }

  /** 테스트용 생성자 — RestTemplate을 외부에서 주입할 수 있다. */
  DataDragonPatchSyncScheduler(RestTemplate restTemplate, PatchVersionService patchVersionService) {
    this.restTemplate = restTemplate;
    this.patchVersionService = patchVersionService;
  }

  @Scheduled(fixedDelayString = "${patch-sync.interval-ms:21600000}")
  public void syncLatestPatch() {
    try {
      String[] versions = restTemplate.getForObject(VERSIONS_URL, String[].class);
      if (versions == null || versions.length == 0) {
        log.warn("[PatchSync] versions.json returned empty");
        return;
      }

      // versions.json은 최신순 정렬 — 첫 번째가 최신 버전
      String latestFullVersion = versions[0];
      String normalizedPatch = normalizePatch(latestFullVersion);

      if (normalizedPatch == null) {
        log.warn("[PatchSync] Failed to normalize version: {}", latestFullVersion);
        return;
      }

      boolean registered = patchVersionService.registerIfAbsent(
          normalizedPatch,
          OffsetDateTime.now()
      );

      if (registered) {
        log.info("[PatchSync] New patch registered: {} (from {})", normalizedPatch, latestFullVersion);
      } else {
        log.debug("[PatchSync] Patch already exists: {}", normalizedPatch);
      }

    } catch (Exception e) {
      // Data Dragon 장애 시에도 기존 패치 데이터로 계속 운영
      log.error("[PatchSync] Failed to sync from Data Dragon", e);
    }
  }

  /**
   * {@code "15.23.1"} → {@code "15.23"} (major.minor만 추출).
   *
   * <p>BottomDuoExtractor.toPatch()와 동일한 정규화 로직.
   */
  static String normalizePatch(String fullVersion) {
    if (fullVersion == null || fullVersion.isBlank()) {
      return null;
    }
    String[] parts = fullVersion.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    return parts[0] + "." + parts[1];
  }
}
