package com.bestduo_BE.application;

import com.bestduo_BE.application.port.MatchQueuePicker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchDetailQueueWorker {

  private final MatchQueuePicker picker;
  private final CollectMatchDetailAndSaveRaw collectMatchDetailAndSaveRaw;

  /**
   * match_queue에서 READY/ERROR 항목을 뽑아
   * Phase1(CollectMatchDetailAndSaveRaw)만 수행한다.
   */
  public Result execute(int limit) {
    int processed = 0;
    int rawCreated = 0;

    for (MatchQueuePicker.MatchQueueItem item : picker.pick(limit)) {
      String matchId = item.matchId();
      try {
        picker.markRunning(matchId);

        var r = collectMatchDetailAndSaveRaw.execute(matchId, item.tier());
        rawCreated += r.rawCreated();

        picker.markDone(matchId);
        processed++;

      } catch (Exception e) {
        log.error("MatchDetailQueueWorker failed. matchId={}", matchId, e);
        picker.markError(matchId, shorten(e.getMessage()));
      }
    }

    return new Result(processed, rawCreated);
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= 500 ? s : s.substring(0, 500);
  }

  public record Result(int processed, int rawCreated) {}
}
