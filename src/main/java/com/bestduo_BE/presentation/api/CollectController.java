package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.CollectMatchDetailAndSaveRaw;
import com.bestduo_BE.domain.model.Tier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/collect")
public class CollectController {

  private final CollectMatchDetailAndSaveRaw useCase;


  @PostMapping("/match/{matchId}")
  public String collectMatch(
      @PathVariable String matchId,
      @RequestParam(name = "tier", defaultValue = "EMERALD") Tier tier
  ) {
    int created = useCase.execute(matchId, tier);
    return "OK raw_created=" + created;
  }
}
