package com.bestduo_BE.coverage.infra.persistence.repository;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageBucketJpaRepository extends JpaRepository<CoverageBucket, Long> {

  boolean existsByPatchAndTier(String patch, Tier tier);

  Optional<CoverageBucket> findByPatchAndTier(String patch, Tier tier);

  List<CoverageBucket> findAllByOrderByIdAsc();
}
