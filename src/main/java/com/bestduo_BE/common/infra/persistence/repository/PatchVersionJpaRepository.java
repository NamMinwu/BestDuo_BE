package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchVersionJpaRepository extends JpaRepository<PatchVersion, Long> {

  boolean existsByPatch(String patch);

  Optional<PatchVersion> findByPatch(String patch);

  Optional<PatchVersion> findTopByOrderByReleasedAtDesc();

  /** 최신 2개 패치를 최신 순으로 반환 (grace period 판별용) */
  List<PatchVersion> findTop2ByOrderByReleasedAtDesc();
}
