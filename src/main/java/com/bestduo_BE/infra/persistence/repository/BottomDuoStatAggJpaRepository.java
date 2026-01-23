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
      insert into bottom_duo_stat_agg(adc_champion_id, sup_champion_id, tier, wins, games, created_at, updated_at)
      select
        r.adc_champion_id,
        r.sup_champion_id,
        r.collection_tier as tier,
        sum(case when r.win = true then 1 else 0 end) as wins,
        count(*) as games,
        now(),
        now()
      from bottom_duo_raw r
      group by r.adc_champion_id, r.sup_champion_id, r.collection_tier
      on conflict (adc_champion_id, sup_champion_id, tier)
      do update set
        wins = excluded.wins,
        games = excluded.games,
        updated_at = now()
      """, nativeQuery = true)
  int upsertAllFromRaw();

  @Query(value = """
      select coalesce(sum(games), 0)
      from bottom_duo_stat_agg
      where tier = :tier
      """, nativeQuery = true)
  int sumGamesByTier(@Param("tier") String tier);

  // ✅ WINRATE DESC
  @Query(value = """
      select *
      from bottom_duo_stat_agg
      where tier = :tier
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopWinRateDesc(
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
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) asc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopWinRateAsc(
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
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when :totalGames = 0 then 0 else (games::double precision / :totalGames) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopPickRateDesc(
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
        and (:adc is null or adc_champion_id = :adc)
        and (:sup is null or sup_champion_id = :sup)
      order by
        (case when :totalGames = 0 then 0 else (games::double precision / :totalGames) end) asc,
        games asc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoStatAgg> findTopPickRateAsc(
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("totalGames") int totalGames,
      @Param("limit") int limit
  );
}
