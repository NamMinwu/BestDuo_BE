package com.bestduo_BE.presentation.api;


import com.bestduo_BE.application.ViewBottomDuoStatistics;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bottom-duo/stats")
@RequiredArgsConstructor
public class BottomDuoStatisticsController {
  private final ViewBottomDuoStatistics viewBottomDuoStatistics; // 유즈케이스

  @GetMapping
  public ResponseEntity<BottomDuoStatisticsResponse> getBottomDuoStats(
      @RequestParam(required = false) String adChampionId,
      @RequestParam(required = false) String supChampionId,
      @RequestParam(defaultValue = "ALL_TIERS") Tier tier,
      @RequestParam(defaultValue = "WIN_RATE") SortOption sortOption
  ) {
    BottomDuoStatisticsRequest request = new BottomDuoStatisticsRequest();

    BottomDuoStatisticsResponse response = viewBottomDuoStatistics.handle(request);

    return ResponseEntity.ok(response);
  }
}
