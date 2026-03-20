package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {
  List<Summoner> findByExpandStatusOrderByUpdatedAtAsc(String expandStatus, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO summoner (
          puuid,
          seed_status,
          expand_status,
          refresh_status,
          last_seed_run_at,
          last_expand_run_at,
          last_refresh_run_at,
          last_match_start_time,
          created_at,
          updated_at)
      VALUES (:puuid, 'READY', 'READY', 'READY', NULL, NULL, NULL, NULL, :now, :now)
      ON CONFLICT (puuid) DO NOTHING
      """, nativeQuery = true)
  int insertReadyIfAbsent(@Param("puuid") String puuid, @Param("now") OffsetDateTime now);

  // 최소 구현: refresh 대상 일부 뽑기 (나중에 READY/ERROR 우선 정렬로 개선 가능)
  @Query(value = """
      select s.*
      from summoner s
      where s.refresh_status in ('READY','ERROR','DONE')
      order by s.updated_at asc
      limit :limit
      """, nativeQuery = true)
  List<Summoner> findRefreshTargets(int limit);

}
