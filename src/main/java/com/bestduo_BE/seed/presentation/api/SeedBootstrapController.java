package com.bestduo_BE.seed.presentation.api;

import com.bestduo_BE.seed.application.SeedBootstrapRun;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seed")
public class SeedBootstrapController {

  private final SeedBootstrapRun useCase;

  @PostMapping("/bootstrap")
  public SeedBootstrapRun.SeedBootstrapResult run(
      @RequestParam String queue,
      @RequestParam String tier,
      @RequestParam String division,
      @RequestParam(defaultValue = "EMERALD") Tier seedTier,
      @RequestParam(defaultValue = "1") int startPage,
      @RequestParam(defaultValue = "3") int endPage,
      @RequestParam(defaultValue = "10") int matchesPerPuuid,
      @RequestParam(defaultValue = "0") int maxEntries
  ) {
    SeedBootstrapCommand cmd = new SeedBootstrapCommand(
        queue, tier, division, seedTier, startPage, endPage, matchesPerPuuid, maxEntries
    );
    return useCase.execute(cmd);
  }
}
