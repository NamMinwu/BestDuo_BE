package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.ViewBottomDuoCounters;
import com.bestduo_BE.application.ViewBottomDuoDetailStatistics;
import com.bestduo_BE.application.port.BottomDuoMatchupFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoCounterResponse;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bottom-duo")
@Tag(
    name = "바텀 듀오 상세 통계",
    description = "특정 바텀 듀오의 매치업 및 카운터 정보를 상세하게 조회하는 API입니다."
)
public class BottomDuoDetailStatisticsController {

  private final ViewBottomDuoDetailStatistics useCase;
  private final ViewBottomDuoCounters counterUseCase;

  @GetMapping("/matchups")
  @Operation(
      summary = "바텀 듀오 매치업 통계 조회",
      description = "선택한 바텀 듀오가 다른 바텀 듀오를 상대로 했던 승률, KDA, 평균 게임 시간 등 매치업 관련 상세 통계를 조회합니다."
  )
  public BottomDuoDetailStatisticsResponse getList(
      @RequestParam Tier tier,
      @RequestParam(required = false) String patchVersion,
      @RequestParam String adcChampionId,
      @RequestParam String supChampionId,
      @RequestParam(required = false) String oppAdcChampionId,
      @RequestParam(required = false) String oppSupChampionId,
      @RequestParam(defaultValue = "PICKRATE_DESC") BottomDuoMatchupFinder.SortKey sort
  ) {
    return useCase.execute(
        tier,
        patchVersion,
        adcChampionId,
        supChampionId,
        oppAdcChampionId,
        oppSupChampionId,
        sort
    );
  }

  // ✅ 카운터: 최저 승률 N개만 반환
  @GetMapping("/counters")
  @Operation(
      summary = "바텀 듀오 카운터 조회",
      description = "선택한 바텀 듀오에게 강한(카운터) 또는 약한 바텀 듀오 목록과 각 매치업의 승률 및 상성 정보를 조회합니다."
  )
  public BottomDuoCounterResponse counters(
      @RequestParam Tier tier,
      @RequestParam(required = false) String patchVersion,
      @RequestParam String adcChampionId,
      @RequestParam String supChampionId,
      @RequestParam(required = false) Integer size
  ) {
    return counterUseCase.execute(tier, patchVersion, adcChampionId, supChampionId, size);
  }

}
