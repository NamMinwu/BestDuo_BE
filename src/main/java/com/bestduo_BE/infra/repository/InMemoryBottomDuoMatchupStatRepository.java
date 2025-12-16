package com.bestduo_BE.infra.repository;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.domain.repository.BottomDuoMatchupStatRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Simple in-memory implementation for wiring the application without persistence. */
@Repository
public class InMemoryBottomDuoMatchupStatRepository implements BottomDuoMatchupStatRepository {

  private final List<BottomDuoMatchupStat> storage = new CopyOnWriteArrayList<>();

  @Override
  public List<BottomDuoMatchupStat> findBy(BottomDuoFilterCriteria criteria) {
    return storage.stream()
        .filter(stat -> matches(stat, criteria))
        .sorted(resolveComparator(criteria))
        .collect(Collectors.toList());
  }

  public void save(List<BottomDuoMatchupStat> stats) {
    if (stats == null || stats.isEmpty()) {
      return;
    }
    storage.addAll(stats);
  }

  private boolean matches(BottomDuoMatchupStat stat, BottomDuoFilterCriteria criteria) {
    if (criteria == null) {
      return true;
    }

    if (!Objects.equals(criteria.getAdCampionId(), stat.getMyAdId())) {
      if (criteria.getAdCampionId() != null) {
        return false;
      }
    }

    if (!Objects.equals(criteria.getSupCampionId(), stat.getMySupId())) {
      if (criteria.getSupCampionId() != null) {
        return false;
      }
    }

    Tier tier = criteria.getTier();
    if (tier != null && tier != Tier.ALL_TIERS && stat.getTier() != tier) {
      return false;
    }

    return true;
  }

  private Comparator<BottomDuoMatchupStat> resolveComparator(BottomDuoFilterCriteria criteria) {
    SortOption sortOption = criteria == null ? null : criteria.getSortOption();

    if (sortOption == SortOption.PICK_RATE) {
      return Comparator.comparingInt(BottomDuoMatchupStat::getGames).reversed();
    }

    return Comparator.comparingDouble(BottomDuoMatchupStat::getWinRate).reversed();
  }
}
