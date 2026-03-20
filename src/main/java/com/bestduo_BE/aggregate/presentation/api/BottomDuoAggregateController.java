package com.bestduo_BE.aggregate.presentation.api;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoStats;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/aggregate")
public class BottomDuoAggregateController {

  private final AggregateBottomDuoStats useCase;

  @PostMapping("/bottom-duo-stat")
  public AggregateBottomDuoStats.Result aggregate() {
    return useCase.execute();
  }
}
