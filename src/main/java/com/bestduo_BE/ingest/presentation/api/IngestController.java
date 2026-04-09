package com.bestduo_BE.ingest.presentation.api;

import com.bestduo_BE.ingest.application.IngestMatchDetail;
import com.bestduo_BE.common.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ingest")
public class IngestController {

  private final IngestMatchDetail useCase;


  @PostMapping("/match/{matchId}")
  public String ingestMatch(
      @PathVariable String matchId,
      @RequestParam(name = "tier", defaultValue = "EMERALD") Tier tier
  ) {
    int created = useCase.execute(matchId, tier, null).rawCreated();
    return "OK raw_created=" + created;
  }
}
