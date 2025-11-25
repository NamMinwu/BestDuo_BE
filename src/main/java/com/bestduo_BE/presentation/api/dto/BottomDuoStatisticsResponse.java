package com.bestduo_BE.presentation.api.dto;

import com.bestduo_BE.domain.model.BottomDuoStat;
import java.util.List;

public record BottomDuoStatisticsResponse(
) {
  public static BottomDuoStatisticsResponse from(List<BottomDuoStat> stats){
    return new BottomDuoStatisticsResponse();
  }

}
