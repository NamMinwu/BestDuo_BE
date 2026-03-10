package com.bestduo_BE.application;

import com.bestduo_BE.application.port.MatchQueueStatusCount;
import com.bestduo_BE.infra.persistence.repository.MatchQueueStatsJpaRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueStats {

  private final MatchQueueStatsJpaRepository matchQueueStatsJpaRepository;

  public Result getStats() {
    // 1) status counts
    Map<String, Long> statusCounts = new HashMap<>();
    for (MatchQueueStatusCount row : matchQueueStatsJpaRepository.countByStatus()) {
      statusCounts.put(row.getStatus(), row.getCnt());
    }

    // 2) stale running
    int staleMinutes = 10;
    long staleRunning = matchQueueStatsJpaRepository.countStaleRunning(staleMinutes);

    // 3) retry distribution
    List<RetryBucket> retryBuckets = matchQueueStatsJpaRepository.countErrorByRetryCount().stream()
        .map(r -> new RetryBucket(r.getRetryCount(), r.getCnt()))
        .toList();

    // 4) top errors
    List<ErrorTop> topErrors = matchQueueStatsJpaRepository.topErrors(10).stream()
        .map(e -> new ErrorTop(e.getLastError(), e.getCnt()))
        .toList();

    Instant oldestReadyInstant = matchQueueStatsJpaRepository.findOldestReadyUpdatedAt();
    OffsetDateTime oldestReady = OffsetDateTime.ofInstant(oldestReadyInstant, ZoneId.systemDefault());
    long doneLast10m = matchQueueStatsJpaRepository.countDoneInLastMinutes(10);

    return new Result(statusCounts, staleMinutes, staleRunning, retryBuckets, topErrors, oldestReady, doneLast10m);
  }

  public record Result(
      Map<String, Long> statusCounts,
      int staleMinutes,
      long staleRunningCount,
      List<RetryBucket> errorRetryDistribution,
      List<ErrorTop> topErrors,
      OffsetDateTime oldestReadyUpdatedAt,
      long doneLast10Minutes
  ) {}

  public record RetryBucket(int retryCount, long count) {}
  public record ErrorTop(String lastError, long count) {}
}
