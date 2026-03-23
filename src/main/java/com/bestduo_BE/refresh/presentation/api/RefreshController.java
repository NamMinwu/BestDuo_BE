package com.bestduo_BE.refresh.presentation.api;

import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.refresh.application.RefreshSummonerMatches;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/refresh")
public class RefreshController {

  private final RefreshSummonerMatches refreshSummonerMatches;
  private final RefreshBatchExecutor refreshBatchRun;

  @PostMapping("/one")
  public RefreshSummonerMatches.Result one(@RequestParam String puuid) {
    return refreshSummonerMatches.execute(puuid);
  }

  @PostMapping("/batch")
  public RefreshBatchExecutor.Result batch(@RequestParam(defaultValue = "50") int limit) {
    return refreshBatchRun.execute(limit);
  }
}
