package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.infra.persistence.entity.Summoner;
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
      INSERT INTO summoner (puuid, seed_status, expand_status, last_seed_run_at, last_expand_run_at, created_at, updated_at)
      VALUES (:puuid, 'READY', 'READY', NULL, NULL, :now, :now)
      ON CONFLICT (puuid) DO NOTHING
      """, nativeQuery = true)
  int insertReadyIfAbsent(@Param("puuid") String puuid, @Param("now") OffsetDateTime now);
}
