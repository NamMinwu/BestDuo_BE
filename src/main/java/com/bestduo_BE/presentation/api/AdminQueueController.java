package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.QueueStats;
import com.bestduo_BE.application.QueueStats.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/queue")
public class AdminQueueController {

  private final QueueStats queueStats;

  @GetMapping("/stats")
  public Result stats() {
    return queueStats.getStats();
  }
}
