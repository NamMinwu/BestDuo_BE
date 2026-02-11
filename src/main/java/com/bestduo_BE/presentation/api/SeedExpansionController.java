package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.SeedExpansionWorker;
import com.bestduo_BE.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seed")
public class SeedExpansionController {

  private final SeedExpansionWorker worker;

  @PostMapping("/expand")
  public SeedExpansionWorker.ExpansionResult expand(
      @RequestParam(defaultValue = "50") int batchSize,
      @RequestParam(defaultValue = "5") int matchesPerPuuid,
      @RequestParam(defaultValue = "EMERALD") Tier collectionTier
  ) {
    return worker.execute(batchSize, matchesPerPuuid, collectionTier);
  }
}
