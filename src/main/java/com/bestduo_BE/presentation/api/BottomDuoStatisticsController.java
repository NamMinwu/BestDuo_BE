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
    // 컨트롤러용 Request DTO로 묶어주기
    BottomDuoStatisticsRequest request = new BottomDuoStatisticsRequest();
    // 유즈케이스 호출
    BottomDuoStatisticsResponse response = viewBottomDuoStatistics.handle(request);
    // 그대로 응답
    return ResponseEntity.ok(response);
  }
}
