package com.bestduo_BE.ingest.presentation.api;

import com.bestduo_BE.ingest.application.MatchIngestWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/queue")
public class MatchQueueAdminController {

  private final MatchIngestWorker matchIngestWorker;

  @PostMapping("/work")
  public MatchIngestWorker.Result work(@RequestParam(defaultValue = "20") int limit) {
    return matchIngestWorker.execute(limit);
  }
}
