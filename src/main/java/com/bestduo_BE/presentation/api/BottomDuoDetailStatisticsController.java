package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.ViewBottomDuoDetailStatistics;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bottom-duo/detail-stats")
@RequiredArgsConstructor
public class BottomDuoDetailStatisticsController {

  private final ViewBottomDuoDetailStatistics viewBottomDuoDetailStatistics;

  @GetMapping
  public ResponseEntity<BottomDuoDetailStatisticsResponse> getBottomDuoDetailStats(
      @RequestParam String adChampionId,
      @RequestParam String supChampionId,
      @RequestParam(defaultValue = "ALL_TIERS") Tier tier,
      @RequestParam(defaultValue = "WIN_RATE") SortOption sortOption
  ) {
    /*
     * (입력)
     *  - 선택된 조합 식별하기
     *  - 필터 조건 해석하기 (티어/큐/패치 등)
     * (조회)
     *  - 선택된 조합의 기본 통계 조회하기
     *  - 선택된 조합의 매치업 통계 리스트 조회하기
     * (가공)
     *  - 최소 게임 수 기준으로 매치업 리스트 필터링하기
     *  - 각 매치업별 승률/게임 수 계산하기
     *  - 필터 기준으로 매치업 리스트 정렬하기
     *  - 최저 승률 N개를 카운터 후보로 선정하기
     *  - 챔피언 메타데이터(이름/이미지) 붙이기
     *  - 결과를 응답 모델로 변환하기
     *  - 전체 매치업 수(totalMatchups) 계산하기
     * (저장)
     *  - 매치업 데이터 생성하기
     */
    // (입력) 조합/필터 파라미터를 받아서 도메인 요청 모델 생성
    BottomDuoDetailStatisticsRequest request = new BottomDuoDetailStatisticsRequest(
        adChampionId,
        supChampionId,
        tier,
        sortOption
    );

    // (조회/가공/저장) 나머지 단계는 유즈케이스 내부에서 처리
    BottomDuoDetailStatisticsResponse response = viewBottomDuoDetailStatistics.handle(request);

    return ResponseEntity.ok(response);
  }
}
