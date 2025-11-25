package com.bestduo_BE.application.filter;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsRequest;
import org.springframework.stereotype.Component;


@Component
public class BottomDuoFilterParser {
  public BottomDuoFilterCriteria parse(BottomDuoStatisticsRequest request) {
    // request에서 ad, sup, tier, sortOption, minPickRate 등 추출 + 검증
    return null;
  }

}
