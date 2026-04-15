package com.bestduo_BE.ingest.presentation.api;

import com.bestduo_BE.ingest.application.IngestQueueStats;
import com.bestduo_BE.ingest.application.IngestQueueStats.Result;
import com.bestduo_BE.ingest.application.MatchIngestRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/queue")
public class AdminQueueController {

  private final IngestQueueStats queueStats;
  private final MatchIngestRunner matchIngestRunner;

  @GetMapping("/stats")
  public Result stats() {
    return queueStats.getStats();
  }

  @PostMapping("/work")
  public MatchIngestRunner.Result work(@RequestParam(defaultValue = "20") int limit) {
    return matchIngestRunner.executeWithPriority(limit, null, null);
  }
}
