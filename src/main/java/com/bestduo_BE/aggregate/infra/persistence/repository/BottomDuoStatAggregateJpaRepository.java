package com.bestduo_BE.aggregate.infra.persistence.repository;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregate;
import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregateId;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BottomDuoStatAggregateJpaRepository extends JpaRepository<BottomDuoStatAggregate, BottomDuoStatAggregateId> {

  Pattern PATCH_VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

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

  @Query("""
      select distinct b.patchVersion
      from BottomDuoStatAggregate b
      where b.patchVersion is not null
        and b.patchVersion <> 'UNKNOWN'
      """)
  List<String> findCandidatePatchVersions();

  default String findLatestPatchVersion() {
    return findCandidatePatchVersions().stream()
        .filter(BottomDuoStatAggregateJpaRepository::isParseablePatchVersion)
        .max(Comparator.comparingInt(BottomDuoStatAggregateJpaRepository::patchMajor)
            .thenComparingInt(BottomDuoStatAggregateJpaRepository::patchMinor))
        .orElse(null);
  }

  default String findPreviousPatchVersion(String currentPatch) {
    if (currentPatch == null || !isParseablePatchVersion(currentPatch)) {
      return null;
    }
    return findCandidatePatchVersions().stream()
        .filter(BottomDuoStatAggregateJpaRepository::isParseablePatchVersion)
        .filter(candidate -> comparePatchVersion(candidate, currentPatch) < 0)
        .max(Comparator.comparingInt(BottomDuoStatAggregateJpaRepository::patchMajor)
            .thenComparingInt(BottomDuoStatAggregateJpaRepository::patchMinor))
        .orElse(null);
  }

  List<BottomDuoStatAggregate> findByPatchVersion(String patchVersion);

  List<BottomDuoStatAggregate> findByPatchVersionAndTier(String patchVersion, String tier);

  /**
   * retention 정책: keepPatches에 포함되지 않은 patch_version 행을 모두 삭제한다.
   * <p>호출 측은 keepPatches가 비어있지 않음을 보장해야 한다 (빈 리스트로 호출 시 IllegalArgumentException).
   */
  @Modifying
  @Transactional
  @Query(value = """
      delete from bottom_duo_stat_agg
      where patch_version not in (:keepPatches)
      """, nativeQuery = true)
  int deleteByPatchVersionNotIn(@Param("keepPatches") List<String> keepPatches);

  /** stat_agg에 존재하는 모든 patch_version (정렬 X). */
  @Query(value = """
      select distinct patch_version
      from bottom_duo_stat_agg
      where patch_version is not null
      """, nativeQuery = true)
  List<String> findAllDistinctPatchVersions();

  private static boolean isParseablePatchVersion(String raw) {
    return raw != null && PATCH_VERSION_PATTERN.matcher(raw).matches();
  }

  private static int comparePatchVersion(String left, String right) {
    int majorCompare = Integer.compare(patchMajor(left), patchMajor(right));
    if (majorCompare != 0) {
      return majorCompare;
    }
    return Integer.compare(patchMinor(left), patchMinor(right));
  }

  private static int patchMajor(String raw) {
    Matcher matcher = PATCH_VERSION_PATTERN.matcher(raw);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unparseable patch version: " + raw);
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int patchMinor(String raw) {
    Matcher matcher = PATCH_VERSION_PATTERN.matcher(raw);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Unparseable patch version: " + raw);
    }
    return Integer.parseInt(matcher.group(2));
  }

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
  List<BottomDuoStatAggregate> findTopWinRateDesc(
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
  List<BottomDuoStatAggregate> findTopWinRateAsc(
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
  List<BottomDuoStatAggregate> findTopPickRateDesc(
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
  List<BottomDuoStatAggregate> findTopPickRateAsc(
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
  List<BottomDuoStatAggregate> findTopDuoTierAsc(
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
  List<BottomDuoStatAggregate> findTopDuoTierDesc(
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
  List<BottomDuoStatAggregate> findTopRankingAsc(
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
  List<BottomDuoStatAggregate> findTopRankingDesc(
      @Param("patchVersion") String patchVersion,
      @Param("tier") String tier,
      @Param("adc") String adc,
      @Param("sup") String sup,
      @Param("limit") int limit
  );
}
