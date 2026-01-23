package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.AggregateBottomDuoStat;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/aggregate")
public class BottomDuoAggregateController {

  private final AggregateBottomDuoStat useCase;

  @PostMapping("/bottom-duo-stat")
  public AggregateBottomDuoStat.Result aggregate() {
    return useCase.execute();
  }
}
