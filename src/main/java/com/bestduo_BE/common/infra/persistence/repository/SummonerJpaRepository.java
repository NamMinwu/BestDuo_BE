package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {

  @Transactional
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

  @Transactional
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

  @Transactional
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
          when :requestedTier is null then 0
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
   * leagueEntryFetchedAt이 있고 matchIdsCollectedAt이 null이거나 leagueEntryFetchedAt보다 이전인 summoner를
   * leagueEntryFetchedAt 최신 순으로 반환한다.
   */
  @Query(value = """
      SELECT s.*
      FROM summoner s
      WHERE s.seeded_at IS NOT NULL
        AND (s.match_ids_collected_at IS NULL OR s.match_ids_collected_at < s.seeded_at)
      ORDER BY
        CASE WHEN :priorityTier IS NOT NULL AND s.last_known_tier = :priorityTier THEN 0 ELSE 1 END,
        CASE s.last_known_tier
          WHEN 'CHALLENGER'  THEN 0
          WHEN 'GRANDMASTER' THEN 1
          WHEN 'MASTER'      THEN 2
          WHEN 'DIAMOND'     THEN 3
          WHEN 'EMERALD'     THEN 4
          ELSE 5
        END,
        s.seeded_at DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<Summoner> findMatchIdsPendingSummoners(
      @Param("priorityTier") String priorityTier, @Param("limit") int limit);

  /** matchIds 수집 대기 중인 summoner 수 (collect_pending_summoners gauge 용). */
  @Query(value = """
      SELECT count(*)
      FROM summoner s
      WHERE s.seeded_at IS NOT NULL
        AND (s.match_ids_collected_at IS NULL OR s.match_ids_collected_at < s.seeded_at)
      """, nativeQuery = true)
  long countMatchIdsPendingSummoners();

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE summoner
      SET seeded_at = :fetchedAt,
          updated_at = now()
      WHERE puuid = :puuid
      """, nativeQuery = true)
  int markLeagueEntryFetched(@Param("puuid") String puuid, @Param("fetchedAt") OffsetDateTime fetchedAt);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE summoner
      SET match_ids_collected_at = :collectedAt,
          updated_at = now()
      WHERE puuid = :puuid
      """, nativeQuery = true)
  int markMatchIdsCollected(@Param("puuid") String puuid, @Param("collectedAt") OffsetDateTime collectedAt);

  /**
   * Stage 1 upsert: 없으면 등록, 있으면 tier·seeded_at 갱신.
   * 두 경우 모두 seeded_at(leagueEntryFetchedAt)을 주어진 시점으로 설정한다.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO summoner (puuid, last_known_tier, tier_observed_at, seeded_at, created_at, updated_at)
      VALUES (:puuid, :tier, :fetchedAt, :fetchedAt, now(), :fetchedAt)
      ON CONFLICT (puuid) DO UPDATE SET
          last_known_tier  = EXCLUDED.last_known_tier,
          tier_observed_at = EXCLUDED.tier_observed_at,
          seeded_at        = EXCLUDED.seeded_at,
          updated_at       = now()
      """, nativeQuery = true)
  int upsertLeagueEntry(
      @Param("puuid") String puuid,
      @Param("tier") String tier,
      @Param("fetchedAt") OffsetDateTime fetchedAt
  );

}
