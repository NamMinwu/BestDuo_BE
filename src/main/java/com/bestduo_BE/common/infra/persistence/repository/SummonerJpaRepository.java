package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      update summoner
      set last_match_start_time = case
          when :candidateCursor is null then last_match_start_time
          when last_match_start_time is null or last_match_start_time < :candidateCursor then :candidateCursor
          else last_match_start_time
        end,
        updated_at = now()
      where puuid = :puuid
      """, nativeQuery = true)
  int advanceLastMatchStartTime(@Param("puuid") String puuid, @Param("candidateCursor") Long candidateCursor);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO summoner (
          puuid,
          last_match_start_time,
          created_at,
          updated_at)
      VALUES (:puuid, NULL, :now, :now)
      ON CONFLICT (puuid) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("puuid") String puuid, @Param("now") OffsetDateTime now);

  @Query(value = """
      select s.*
      from summoner s
      order by s.updated_at asc
      limit :limit
      """, nativeQuery = true)
  List<Summoner> findRefreshTargets(@Param("limit") int limit);

}
