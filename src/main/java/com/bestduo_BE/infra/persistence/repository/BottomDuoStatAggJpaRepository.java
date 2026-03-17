package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.infra.persistence.entity.BottomDuoStatAgg;
import com.bestduo_BE.infra.persistence.entity.BottomDuoStatAggId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BottomDuoStatAggJpaRepository extends JpaRepository<BottomDuoStatAgg, BottomDuoStatAggId> {
  // ✅ Phase3 집계: raw -> agg upsert
  @Modifying
  @Transactional
  @Query(value = """
      insert into bottom_duo_stat_agg(
        patch_version,
        adc_champion_id,
        sup_champion_id,
        tier,
        wins,
        games,
        win_rate,
        pick_rate,
        adjusted_win_rate,
        rank_score,
        ranking,
        duo_tier,
        previous_ranking,
        rank_delta,
        ranking_status,
        created_at,
        updated_at
      )
      select
        coalesce(r.patch, 'UNKNOWN') as patch_version,
        r.adc_champion_id,
        r.sup_champion_id,
        r.collection_tier as tier,
        sum(case when r.win = true then 1 else 0 end) as wins,
        count(*) as games,
        case when count(*) = 0 then 0
             else sum(case when r.win = true then 1 else 0 end)::double precision / count(*) end as win_rate,
        0 as pick_rate,
        case when count(*) = 0 then 0
             else (sum(case when r.win = true then 1 else 0 end)::double precision + 25.0) / (count(*) + 50) end as adjusted_win_rate,
        0 as rank_score,
        null as ranking,
        null as duo_tier,
        null as previous_ranking,
        null as rank_delta,
        'PENDING' as ranking_status,
        now(),
        now()
      from bottom_duo_raw r
      group by
        coalesce(r.patch, 'UNKNOWN'),
        r.adc_champion_id,
        r.sup_champion_id,
        r.collection_tier
      on conflict (patch_version, adc_champion_id, sup_champion_id, tier)
      do update set
        wins = excluded.wins,
        games = excluded.games,
        win_rate = excluded.win_rate,
        pick_rate = excluded.pick_rate,
        adjusted_win_rate = excluded.adjusted_win_rate,
        rank_score = excluded.rank_score,
        ranking_status = excluded.ranking_status,
        updated_at = now()
      """, nativeQuery = true)
  int upsertAllFromRaw();

  @Query(value = """
      select coalesce(sum(games), 0)
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
      """, nativeQuery = true)
  int sumGamesByTier(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier);

  @Query(value = """
      select patch_version
      from bottom_duo_stat_agg
      order by updated_at desc
      limit 1
      """, nativeQuery = true)
  String findLatestPatchVersion();

  @Query(value = """
      select patch_version
      from bottom_duo_stat_agg
      where patch_version < :currentPatch
      order by patch_version desc
      limit 1
      """, nativeQuery = true)
  String findPreviousPatchVersion(@Param("currentPatch") String currentPatch);

  List<BottomDuoStatAgg> findByPatchVersion(String patchVersion);

  // ✅ WINRATE DESC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopWinRateDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );

  // ✅ WINRATE ASC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) asc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopWinRateAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );

  // ✅ PICKRATE DESC (totalGames 파라미터 필요)
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when :totalGames = 0 then 0 else (games::double precision / :totalGames) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopPickRateDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("totalGames") int totalGames,
      @Param("limit") int limit
  );

  // ✅ PICKRATE ASC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when :totalGames = 0 then 0 else (games::double precision / :totalGames) end) asc,
        games asc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopPickRateAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("totalGames") int totalGames,
      @Param("limit") int limit
  );

  // ✅ DUO TIER ASC/DESC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by duo_tier asc nulls last,
        rank_score desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopDuoTierAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );

  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by duo_tier desc nulls last,
        rank_score desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopDuoTierDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );

  // ✅ RANKING ASC/DESC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by ranking asc nulls last,
        rank_score desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopRankingAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );

  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and patch_version = :patchVersion
        and ranking_status = 'RANKED'
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by ranking desc nulls last,
        rank_score desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopRankingDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );
}
