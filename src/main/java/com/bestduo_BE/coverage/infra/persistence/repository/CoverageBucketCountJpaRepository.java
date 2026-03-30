package com.bestduo_BE.coverage.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CoverageBucketCountJpaRepository extends Repository<BottomDuoRawEntity, BottomDuoRawId> {

  @Query(value = """
      select count(distinct r.match_id)
      from bottom_duo_raw r
      where r.patch = :patch
        and r.collection_tier = :tier
      """, nativeQuery = true)
  long countDistinctMatches(@Param("patch") String patch, @Param("tier") String tier);
}
