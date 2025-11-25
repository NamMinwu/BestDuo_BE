package com.bestduo_BE.infra.repository;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Simple in-memory repository so we can wire the application without a database yet.
 */
@Repository
public class InMemoryBottomDuoStatRepository implements BottomDuoStatRepository {

  private final Map<String, BottomDuoStat> storage = new ConcurrentHashMap<>();

  @Override
  public List<BottomDuoStat> findBy(BottomDuoFilterCriteria criteria) {
    return storage.values().stream()
        .filter(stat -> matches(stat, criteria))
        .sorted(resolveComparator(criteria))
        .collect(Collectors.toList());
  }

  @Override
  public void save(List<BottomDuoStat> stats) {
    if (stats == null || stats.isEmpty()) {
      return;
    }
    stats.forEach(stat -> storage.put(buildKey(stat), stat));
  }

  private Comparator<BottomDuoStat> resolveComparator(BottomDuoFilterCriteria criteria) {
    SortOption sortOption = criteria == null ? null : criteria.getSortOption();

    if (sortOption == SortOption.PICK_RATE) {
      return Comparator.comparingInt(BottomDuoStat::getGames).reversed();
    }

    return Comparator.comparingDouble(BottomDuoStat::getWinRate).reversed();
  }

  private boolean matches(BottomDuoStat stat, BottomDuoFilterCriteria criteria) {
    if (criteria == null) {
      return true;
    }

    if (!Objects.equals(criteria.getAdCampionId(), stat.getAdChampionId())) {
      if (criteria.getAdCampionId() != null) {
        return false;
      }
    }

    if (!Objects.equals(criteria.getSupCampionId(), stat.getSupChampionId())) {
      if (criteria.getSupCampionId() != null) {
        return false;
      }
    }

    Tier tierRange = criteria.getTierRange();
    if (tierRange != null && tierRange != Tier.ALL_TIERS && stat.getTier() != tierRange) {
      return false;
    }

    return true;
  }

  private String buildKey(BottomDuoStat stat) {
    return stat.getAdChampionId() + "|" + stat.getSupChampionId() + "|" + stat.getTier().name();
  }
}
