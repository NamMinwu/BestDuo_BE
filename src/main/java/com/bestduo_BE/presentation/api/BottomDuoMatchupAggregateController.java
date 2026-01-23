package com.bestduo_BE.presentation.api;


import com.bestduo_BE.application.AggregateBottomDuoMatchup;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/aggregate")
public class BottomDuoMatchupAggregateController {

  private final AggregateBottomDuoMatchup useCase;

  @PostMapping("/bottom-duo-matchup")
  public AggregateBottomDuoMatchup.Result run() {
    return useCase.execute();
  }
}
