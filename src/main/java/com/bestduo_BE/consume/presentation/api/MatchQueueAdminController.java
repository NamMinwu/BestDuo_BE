package com.bestduo_BE.consume.presentation.api;

import com.bestduo_BE.consume.application.MatchDetailQueueWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/queue")
public class MatchQueueAdminController {

  private final MatchDetailQueueWorker matchDetailQueueWorker;

  @PostMapping("/work")
  public MatchDetailQueueWorker.Result work(@RequestParam(defaultValue = "20") int limit) {
    return matchDetailQueueWorker.execute(limit);
  }
}
