package com.bestduo_BE.aggregate.infra.persistence.repository;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoMatchupAggregate;
import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoMatchupAggregateId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BottomDuoMatchupAggregateJpaRepository extends JpaRepository<BottomDuoMatchupAggregate, BottomDuoMatchupAggregateId> {
  /**
   * raw A(team) vs raw B(other team) self-join으로 matchup 생성.
   * team_id가 다르면 A->B, B->A가 둘 다 만들어져서 "내 듀오 기준" 조회가 가능해짐.
   */
  @Modifying
  @Transactional
  @Query(value = """
      insert into bottom_duo_matchup_agg(
        patch_version,
        my_adc_champion_id, my_sup_champion_id,
        opp_adc_champion_id, opp_sup_champion_id,
        tier, wins, games, created_at, updated_at
      )
      select
        coalesce(a.patch, 'UNKNOWN') as patch_version,
        a.adc_champion_id as my_adc,
        a.sup_champion_id as my_sup,
        b.adc_champion_id as opp_adc,
        b.sup_champion_id as opp_sup,
        a.collection_tier as tier,
        sum(case when a.win = true then 1 else 0 end) as wins,
        count(*) as games,
        now(),
        now()
      from bottom_duo_raw a
      join bottom_duo_raw b
        on a.match_id = b.match_id
       and a.collection_tier = b.collection_tier
       and a.team_id <> b.team_id
      group by coalesce(a.patch, 'UNKNOWN'), a.adc_champion_id, a.sup_champion_id, b.adc_champion_id, b.sup_champion_id, a.collection_tier
      on conflict (patch_version, my_adc_champion_id, my_sup_champion_id, opp_adc_champion_id, opp_sup_champion_id, tier)
      do update set
        wins = excluded.wins,
        games = excluded.games,
        updated_at = now()
      """, nativeQuery = true)
  int upsertAllFromRaw();

  @Query(value = """
      select coalesce(sum(games), 0)
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
      """, nativeQuery = true)
  int sumGamesOfMyDuo(@Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup);

  @Query(value = """
      select patch_version
      from bottom_duo_matchup_agg
      order by updated_at desc
      limit 1
      """, nativeQuery = true)
  String findLatestPatchVersion();

  // WINRATE DESC
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
        and (:oppAdc is null or opp_adc_champion_id = :oppAdc)
        and (:oppSup is null or opp_sup_champion_id = :oppSup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoMatchupAggregate> findTopWinRateDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup,
      @Param("oppAdc") String oppAdc,
      @Param("oppSup") String oppSup,
      @Param("limit") int limit
  );

  // WINRATE ASC
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
        and (:oppAdc is null or opp_adc_champion_id = :oppAdc)
        and (:oppSup is null or opp_sup_champion_id = :oppSup)
      order by
        (case when games = 0 then 0 else (wins::double precision / games) end) asc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoMatchupAggregate> findTopWinRateAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup,
      @Param("oppAdc") String oppAdc,
      @Param("oppSup") String oppSup,
      @Param("limit") int limit
  );

  // PICKRATE DESC (myTotalGames 파라미터 필요)
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
        and (:oppAdc is null or opp_adc_champion_id = :oppAdc)
        and (:oppSup is null or opp_sup_champion_id = :oppSup)
      order by
        (case when :myTotalGames = 0 then 0 else (games::double precision / :myTotalGames) end) desc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoMatchupAggregate> findTopPickRateDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup,
      @Param("oppAdc") String oppAdc,
      @Param("oppSup") String oppSup,
      @Param("myTotalGames") int myTotalGames,
      @Param("limit") int limit
  );

  // PICKRATE ASC
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
        and (:oppAdc is null or opp_adc_champion_id = :oppAdc)
        and (:oppSup is null or opp_sup_champion_id = :oppSup)
      order by
        (case when :myTotalGames = 0 then 0 else (games::double precision / :myTotalGames) end) asc,
        games asc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoMatchupAggregate> findTopPickRateAsc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup,
      @Param("oppAdc") String oppAdc,
      @Param("oppSup") String oppSup,
      @Param("myTotalGames") int myTotalGames,
      @Param("limit") int limit
  );

  // ✅ COUNTER: 최저 승률 N개 (승률 오름차순)
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
     order by
        (case when games = 0 then 0 else (wins::double precision / games) end) asc,
        games desc
      limit :limit
      """, nativeQuery = true)
  List<BottomDuoMatchupAggregate> findCountersByLowestWinRate(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("myAdc") String myAdc,
      @Param("mySup") String mySup,
      @Param("limit") int limit
  );
}
