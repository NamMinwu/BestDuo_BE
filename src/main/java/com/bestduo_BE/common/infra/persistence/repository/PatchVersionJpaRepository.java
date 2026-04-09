package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchVersionJpaRepository extends JpaRepository<PatchVersion, Long> {

  boolean existsByPatch(String patch);

  Optional<PatchVersion> findByPatch(String patch);

  Optional<PatchVersion> findTopByOrderByReleasedAtDesc();
}
