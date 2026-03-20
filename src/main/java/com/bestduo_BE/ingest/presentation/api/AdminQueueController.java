package com.bestduo_BE.ingest.presentation.api;

import com.bestduo_BE.ingest.application.MatchQueueStats;
import com.bestduo_BE.ingest.application.MatchQueueStats.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/queue")
public class AdminQueueController {

  private final MatchQueueStats queueStats;

  @GetMapping("/stats")
  public Result stats() {
    return queueStats.getStats();
  }
}
