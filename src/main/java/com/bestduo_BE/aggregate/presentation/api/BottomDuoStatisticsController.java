package com.bestduo_BE.aggregate.presentation.api;


import com.bestduo_BE.aggregate.application.GetBottomDuoStatistics;
import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bottom-duo")
@RequiredArgsConstructor
@Tag(
    name = "바텀 듀오 통계",
    description = "바텀 듀오의 전반적인 통계 정보를 조회하는 API입니다."
)
public class BottomDuoStatisticsController {
  private final GetBottomDuoStatistics useCase;

  @GetMapping("/stats")
  @Operation(
      summary = "바텀 듀오 전체 통계 조회",
      description = "선택한 조건(티어, 시즌, 패치, 큐 타입 등)에 대해 바텀 듀오의 승률, 픽률, 밴률 등의 종합 통계를 조회합니다."
  )
  public BottomDuoStatisticsResponse getList(
      @RequestParam Tier tier,
      @RequestParam(required = false) String patchVersion,
      @RequestParam(required = false) String adcChampionId,
      @RequestParam(required = false) String supChampionId,
      @RequestParam(defaultValue = "PICKRATE_DESC") BottomDuoStatFinder.SortKey sort
  ) {
    return useCase.execute(tier, patchVersion, adcChampionId, supChampionId, sort);
  }
}
