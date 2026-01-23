package com.bestduo_BE.presentation.api;


import com.bestduo_BE.application.ViewBottomDuoStatistics;
import com.bestduo_BE.application.port.BottomDuoStatFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bottom-duo")
@RequiredArgsConstructor
public class BottomDuoStatisticsController {
  private final ViewBottomDuoStatistics useCase;

  @GetMapping("/stats")
  public BottomDuoStatisticsResponse getList(
      @RequestParam Tier tier,
      @RequestParam(required = false) String adcChampionId,
      @RequestParam(required = false) String supChampionId,
      @RequestParam(defaultValue = "PICKRATE_DESC") BottomDuoStatFinder.SortKey sort
  ) {
    return useCase.execute(tier, adcChampionId, supChampionId, sort);
  }
}
