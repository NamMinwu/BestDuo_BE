package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.domain.model.Tier;
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

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      update summoner
      set last_known_tier = :lastKnownTier,
          tier_observed_at = :tierObservedAt,
          updated_at = now()
      where puuid = :puuid
      """, nativeQuery = true)
  int updateTierMetadata(
      @Param("puuid") String puuid,
      @Param("lastKnownTier") String lastKnownTier,
      @Param("tierObservedAt") OffsetDateTime tierObservedAt
  );

  @Query(value = """
      select s.*
      from summoner s
      order by
        case
          when :requestedTier = 'ALL_TIERS' then 0
          when s.last_known_tier = :requestedTier then 0
          when s.last_known_tier is null then 1
          else 2
        end asc,
        case when s.tier_observed_at is null then 0 else 1 end asc,
        s.tier_observed_at asc,
        s.updated_at asc
      limit :limit
      """, nativeQuery = true)
  List<Summoner> findRefreshTargets(@Param("limit") int limit, @Param("requestedTier") String requestedTier);

  long countByLastKnownTier(Tier tier);

  /**
   * matchIds 수집 대기 중인 summoner 조회.
   * seededAt이 있고 matchIdsCollectedAt이 null이거나 seededAt보다 이전인 summoner를
   * seededAt 최신 순으로 반환한다.
   */
  @Query(value = """
      SELECT s.*
      FROM summoner s
      WHERE s.seeded_at IS NOT NULL
        AND (s.match_ids_collected_at IS NULL OR s.match_ids_collected_at < s.seeded_at)
      ORDER BY s.seeded_at DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<Summoner> findMatchIdsPendingSummoners(@Param("limit") int limit);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE summoner
      SET seeded_at = :seededAt,
          updated_at = now()
      WHERE puuid = :puuid
      """, nativeQuery = true)
  int markSeeded(@Param("puuid") String puuid, @Param("seededAt") OffsetDateTime seededAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE summoner
      SET match_ids_collected_at = :collectedAt,
          updated_at = now()
      WHERE puuid = :puuid
      """, nativeQuery = true)
  int markMatchIdsCollected(@Param("puuid") String puuid, @Param("collectedAt") OffsetDateTime collectedAt);

}
