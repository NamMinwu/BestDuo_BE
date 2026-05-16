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

  /**
   * retention 정책: keepPatches에 포함되지 않은 patch_version 행을 모두 삭제한다.
   * <p>호출 측은 keepPatches가 비어있지 않음을 보장해야 한다.
   */
  @Modifying
  @Transactional
  @Query(value = """
      delete from bottom_duo_matchup_agg
      where patch_version not in (:keepPatches)
      """, nativeQuery = true)
  int deleteByPatchVersionNotIn(@Param("keepPatches") List<String> keepPatches);

  /** matchup_agg에 존재하는 모든 patch_version (정렬 X). */
  @Query(value = """
      select distinct patch_version
      from bottom_duo_matchup_agg
      where patch_version is not null
      """, nativeQuery = true)
  List<String> findAllDistinctPatchVersions();

  // ✅ COUNTER: 최저 승률 N개 (베이지안 스무딩 승률 오름차순)
  // 정렬 식: (wins + 20) / (games + 40) — prior 0.5, α=β=20.
  // 표본이 적으면 0.5 근처로 끌려와 카운터 상위 진입 불가. 표본이 커지면 실제 승률에 수렴.
  // 자세한 결정 근거: docs/adr_counter_bayesian_smoothing.md
  @Query(value = """
      select *
      from bottom_duo_matchup_agg
      where tier = :tier
        and patch_version = :patchVersion
        and my_adc_champion_id = :myAdc
        and my_sup_champion_id = :mySup
      order by
        ((wins::double precision) + 20.0) / (games + 40) asc,
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
