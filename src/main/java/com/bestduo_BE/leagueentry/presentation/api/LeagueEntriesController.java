package com.bestduo_BE.leagueentry.presentation.api;

import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher;
import com.bestduo_BE.common.domain.model.LeagueEntriesFetchCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher.LeagueEntriesFetchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seed")
public class LeagueEntriesController {

  private final LeagueEntriesFetcher useCase;

  @PostMapping("/bootstrap")
  public LeagueEntriesFetchResult run(
      @RequestParam String queue,
      @RequestParam String tier,
      @RequestParam String division,
      @RequestParam(defaultValue = "EMERALD") Tier seedTier,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "0") int maxEntries
  ) {
    LeagueEntriesFetchCommand cmd = new LeagueEntriesFetchCommand(
        queue, tier, division, seedTier, page, maxEntries
    );
    return useCase.execute(cmd);
  }
}
