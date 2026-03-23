package com.bestduo_BE.common.infra.persistence;

import com.bestduo_BE.common.application.port.MatchPayloadReader;
import com.bestduo_BE.common.application.port.SummonerRefreshCursorTracker;
import com.bestduo_BE.common.infra.persistence.entity.SummonerRefreshPendingMatch;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerRefreshPendingMatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerRefreshCursorTrackerImpl implements SummonerRefreshCursorTracker {

  private final SummonerRefreshPendingMatchJpaRepository pendingRepository;
  private final SummonerJpaRepository summonerJpaRepository;
  private final MatchPayloadReader matchPayloadReader;

  @Override
  @Transactional(readOnly = true)
  public boolean hasPendingMatches(String puuid) {
    return pendingRepository.existsByIdPuuid(puuid);
  }

  @Override
  @Transactional
  public Long registerRefreshBatch(String puuid, List<String> orderedMatchIds, Long currentCursor) {
    if (orderedMatchIds == null || orderedMatchIds.isEmpty()) {
      return currentCursor;
    }

    List<SummonerRefreshPendingMatch> pending = new ArrayList<>();
    int responseIndex = 0;
    for (String matchId : new LinkedHashSet<>(orderedMatchIds)) {
      if (matchId == null || matchId.isBlank()) {
        continue;
      }
      pending.add(SummonerRefreshPendingMatch.newPending(
          puuid,
          matchId,
          responseIndex++,
          loadStoredMatchStartTime(matchId)
      ));
    }

    if (pending.isEmpty()) {
      return currentCursor;
    }

    pendingRepository.saveAll(pending);
    return releaseConfirmedFrontier(puuid, currentCursor);
  }

  @Override
  @Transactional
  public void confirmMatchIngested(String matchId, Long matchStartTimeSec) {
    if (matchId == null || matchId.isBlank() || matchStartTimeSec == null) {
      return;
    }

    List<SummonerRefreshPendingMatch> rows = pendingRepository.findByIdMatchId(matchId);
    if (rows.isEmpty()) {
      return;
    }

    Set<String> puuids = new LinkedHashSet<>();
    for (SummonerRefreshPendingMatch row : rows) {
      row.confirm(matchStartTimeSec);
      puuids.add(row.getId().getPuuid());
    }
    pendingRepository.saveAll(rows);

    for (String puuid : puuids) {
      releaseConfirmedFrontier(puuid, null);
    }
  }

  private Long releaseConfirmedFrontier(String puuid, Long currentCursor) {
    List<SummonerRefreshPendingMatch> rows = pendingRepository.findByIdPuuidOrderByResponseIndexAsc(puuid);
    if (rows.isEmpty()) {
      return currentCursor;
    }

    List<SummonerRefreshPendingMatch> releasable = new ArrayList<>();
    Long candidateCursor = currentCursor;

    for (int i = rows.size() - 1; i >= 0; i--) {
      SummonerRefreshPendingMatch row = rows.get(i);
      if (row.getMatchStartTimeSec() == null) {
        break;
      }
      releasable.add(row);
      candidateCursor = max(candidateCursor, row.getMatchStartTimeSec());
    }

    if (releasable.isEmpty()) {
      return currentCursor;
    }

    pendingRepository.deleteAll(releasable);
    if (candidateCursor != null) {
      summonerJpaRepository.advanceLastMatchStartTime(puuid, candidateCursor);
    }
    return candidateCursor;
  }

  private Long loadStoredMatchStartTime(String matchId) {
    try {
      RiotMatchDto match = matchPayloadReader.read(matchId);
      if (match == null || match.info() == null || match.info().gameStartTimestamp() == null) {
        return null;
      }
      return match.info().gameStartTimestamp() / 1000L;
    } catch (IllegalStateException e) {
      return null;
    }
  }

  private Long max(Long a, Long b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return Math.max(a, b);
  }
}
