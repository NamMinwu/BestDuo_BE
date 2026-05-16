package com.bestduo_BE.common.infra.persistence.repository;


import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BottomDuoRawJpaRepository extends JpaRepository<BottomDuoRawEntity, BottomDuoRawId> {

  /**
   * retention 정책: keepPatches에 포함되지 않은 patch 행을 모두 삭제한다.
   * <p>NULL patch도 함께 삭제 — patch 미부여 raw는 유실된 메타로 간주.
   * <p>호출 측은 keepPatches가 비어있지 않음을 보장해야 한다.
   */
  @Modifying
  @Transactional
  @Query(value = """
      delete from bottom_duo_raw
      where patch is null or patch not in (:keepPatches)
      """, nativeQuery = true)
  int deleteByPatchNotIn(@Param("keepPatches") List<String> keepPatches);

  /** raw에 존재하는 모든 patch (NULL 제외, 정렬 X). */
  @Query(value = """
      select distinct patch
      from bottom_duo_raw
      where patch is not null
      """, nativeQuery = true)
  List<String> findAllDistinctPatches();
}
