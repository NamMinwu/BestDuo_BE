package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import com.bestduo_BE.config.AggregateProperties;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 최근 N개 patch만 보존하고 그 외 stat_agg / matchup_agg 행을 정리한다.
 * <p>N은 {@link AggregateProperties.Retention#getPatches()}로 외부화.
 * <p>cron 끝에 호출되어 일일 retention 정책 역할.
 *
 * <p>정렬 정책: patch는 "major.minor" 문자열이므로 단순 정렬은 "15.9" > "15.10" 같은
 * 함정이 있다. 항상 정수 split로 비교한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupOldPatches {

  private static final Pattern PATCH_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

  private final BottomDuoStatAggregateJpaRepository statRepo;
  private final BottomDuoMatchupAggregateJpaRepository matchupRepo;
  private final AggregateProperties props;

  public Result execute() {
    int retention = props.getRetention().getPatches();
    Set<String> allPatches = collectAllPatches();

    List<String> parseablePatches = allPatches.stream()
        .filter(CleanupOldPatches::isParseable)
        .toList();

    List<String> keepPatches = parseablePatches.stream()
        .sorted(Comparator
            .comparingInt(CleanupOldPatches::major)
            .thenComparingInt(CleanupOldPatches::minor)
            .reversed())
        .limit(retention)
        .toList();

    if (keepPatches.isEmpty()) {
      log.info("[CleanupOldPatches] no parseable patches, skipping delete (allPatches={})",
          allPatches.size());
      return new Result(0, 0, keepPatches);
    }

    boolean hasNonParseable = allPatches.size() > parseablePatches.size();
    boolean hasExcess = parseablePatches.size() > retention;
    if (!hasNonParseable && !hasExcess) {
      log.info("[CleanupOldPatches] all {} patches within retention={}, skipping delete",
          parseablePatches.size(), retention);
      return new Result(0, 0, keepPatches);
    }

    int statDeleted = statRepo.deleteByPatchVersionNotIn(keepPatches);
    int matchupDeleted = matchupRepo.deleteByPatchVersionNotIn(keepPatches);

    log.info("[CleanupOldPatches] retention={} keep={} stat={} matchup={}",
        retention, keepPatches, statDeleted, matchupDeleted);

    return new Result(statDeleted, matchupDeleted, keepPatches);
  }

  private Set<String> collectAllPatches() {
    Set<String> all = new HashSet<>();
    all.addAll(statRepo.findAllDistinctPatchVersions());
    all.addAll(matchupRepo.findAllDistinctPatchVersions());
    return all;
  }

  private static boolean isParseable(String patch) {
    return patch != null && PATCH_PATTERN.matcher(patch).matches();
  }

  private static int major(String patch) {
    return parseGroup(patch, 1);
  }

  private static int minor(String patch) {
    return parseGroup(patch, 2);
  }

  private static int parseGroup(String patch, int group) {
    Matcher m = PATCH_PATTERN.matcher(patch);
    if (!m.matches()) {
      throw new IllegalArgumentException("Unparseable patch: " + patch);
    }
    return Integer.parseInt(m.group(group));
  }

  public record Result(int statDeleted, int matchupDeleted, List<String> keepPatches) {}
}
