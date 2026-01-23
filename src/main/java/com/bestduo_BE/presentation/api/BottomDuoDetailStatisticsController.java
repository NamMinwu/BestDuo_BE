package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.ViewBottomDuoCounters;
import com.bestduo_BE.application.ViewBottomDuoDetailStatistics;
import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoCounterResponse;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bottom-duo")
public class BottomDuoDetailStatisticsController {

  private final ViewBottomDuoDetailStatistics useCase;
  private final ViewBottomDuoCounters counterUseCase;

  @GetMapping("/matchups")
  public BottomDuoDetailStatisticsResponse getList(
      @RequestParam Tier tier,
      @RequestParam String adcChampionId,
      @RequestParam String supChampionId,
      @RequestParam(required = false) String oppAdcChampionId,
      @RequestParam(required = false) String oppSupChampionId,
      @RequestParam(defaultValue = "PICKRATE_DESC") BottomDuoMatchupFinder.SortKey sort
  ) {
    return useCase.execute(
        tier,
        adcChampionId,
        supChampionId,
        oppAdcChampionId,
        oppSupChampionId,
        sort
    );
  }

  // ✅ 카운터: 최저 승률 N개만 반환
  @GetMapping("/counters")
  public BottomDuoCounterResponse counters(
      @RequestParam Tier tier,
      @RequestParam String adcChampionId,
      @RequestParam String supChampionId,
      @RequestParam(required = false) Integer size
  ) {
    return counterUseCase.execute(tier, adcChampionId, supChampionId, size);
  }

}