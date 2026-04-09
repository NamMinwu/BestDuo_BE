package com.bestduo_BE.common.application;

import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import com.bestduo_BE.common.infra.persistence.repository.PatchVersionJpaRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatchVersionService {

  private final PatchVersionJpaRepository patchVersionRepository;

  /** 최신 패치의 릴리스 시점 (epoch seconds). 데이터 없으면 Optional.empty() */
  public Optional<Long> currentPatchStartTimeEpochSeconds() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc()
        .map(PatchVersion::releasedAtEpochSeconds);
  }

  /** 최신 패치 문자열 (e.g., "15.23"). 데이터 없으면 Optional.empty() */
  public Optional<String> currentPatchVersion() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc()
        .map(PatchVersion::getPatch);
  }

  /** 최신 PatchVersion 엔티티 반환. 데이터 없으면 Optional.empty() */
  public Optional<PatchVersion> currentPatch() {
    return patchVersionRepository.findTopByOrderByReleasedAtDesc();
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
