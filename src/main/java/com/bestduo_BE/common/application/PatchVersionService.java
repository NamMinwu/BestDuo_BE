package com.bestduo_BE.common.application;

import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import com.bestduo_BE.common.infra.persistence.repository.PatchVersionJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatchVersionService {

  /**
   * 새 패치 출시 직후에는 게임 데이터가 부족해 match ID 수집이 빈 결과를 반환한다.
   * 출시 후 이 일수가 지나야 최신 패치를 effective patch로 승격한다.
   */
  private static final int PATCH_GRACE_PERIOD_DAYS = 3;

  private final PatchVersionJpaRepository patchVersionRepository;

  /** 최신 패치의 릴리스 시점 (epoch seconds). 데이터 없으면 Optional.empty() */
  @Deprecated
  public Optional<Long> currentPatchStartTimeEpochSeconds() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc()
        .map(PatchVersion::releasedAtEpochSeconds);
  }

  /** 최신 패치 문자열 (e.g., "15.23"). 데이터 없으면 Optional.empty() */
  @Deprecated
  public Optional<String> currentPatchVersion() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc()
        .map(PatchVersion::getPatch);
  }

  /** 최신 PatchVersion 엔티티 반환. 데이터 없으면 Optional.empty() */
  public Optional<PatchVersion> currentPatch() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc();
  }

  /**
   * 공개용 최근 패치 2개. 최신 패치가 grace period({@code PATCH_GRACE_PERIOD_DAYS}일) 이내면
   * 데이터 신뢰도가 낮아 latest를 제외하고 그 이전 2개를 반환한다.
   * 그 외에는 최신 2개를 그대로 반환한다.
   */
  public List<PatchVersion> recentPatches() {
    List<PatchVersion> top3 = patchVersionRepository.findTop3ByOrderByReleasedAtDesc();
    if (top3.isEmpty()) {
      return List.of();
    }

    PatchVersion latest = top3.get(0);
    boolean inGracePeriod = OffsetDateTime.now()
        .isBefore(latest.getReleasedAt().plusDays(PATCH_GRACE_PERIOD_DAYS));

    int from = inGracePeriod ? 1 : 0;
    int to = Math.min(from + 2, top3.size());
    return List.copyOf(top3.subList(from, to));
  }

  /**
   * match ID 수집에 사용할 "유효 패치" 컨텍스트를 반환한다.
   *
   * <p>최신 패치 출시 후 {@code PATCH_GRACE_PERIOD_DAYS}일 미만이면 grace period:
   * <ul>
   *   <li>이전 패치가 있으면 이전 패치를 effective patch로 사용하고 endTime을 설정한다.</li>
   *   <li>이전 패치가 없으면(패치가 1개뿐) 최신 패치로 graceful fallback (endTime 없음).</li>
   * </ul>
   * 그 외에는 최신 패치를 그대로 사용한다.
   */
  public Optional<EffectivePatchContext> resolveEffectivePatchContext() {
    List<PatchVersion> recent = patchVersionRepository.findTop2ByOrderByReleasedAtDesc();
    if (recent.isEmpty()) {
      return Optional.empty();
    }

    PatchVersion latest = recent.get(0);
    boolean inGracePeriod = OffsetDateTime.now()
        .isBefore(latest.getReleasedAt().plusDays(PATCH_GRACE_PERIOD_DAYS));

    if (inGracePeriod && recent.size() >= 2) {
      PatchVersion previous = recent.get(1);
      return Optional.of(new EffectivePatchContext(
          previous.getPatch(),
          previous.releasedAtEpochSeconds(),
          latest.releasedAtEpochSeconds()
      ));
    }

    return Optional.of(new EffectivePatchContext(
        latest.getPatch(),
        latest.releasedAtEpochSeconds(),
        null
    ));
  }

  /** 새 패치 등록 (멱등: 이미 존재하면 false 반환) */
  public boolean registerIfAbsent(String patch, OffsetDateTime releasedAt) {
    if (patchVersionRepository.existsByPatch(patch)) {
      return false;
    }
    patchVersionRepository.save(PatchVersion.of(patch, releasedAt));
    return true;
  }
}
