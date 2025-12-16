package com.bestduo_BE.application.filter;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsRequest;
import org.springframework.stereotype.Component;

@Component
public class BottomDuoDetailFilterParser {
  public BottomDuoFilterCriteria parse(BottomDuoDetailStatisticsRequest request) {
    if (request == null) {
      return new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.WIN_RATE);
    }

    return new BottomDuoFilterCriteria(
        request.adChampionId(),
        request.supChampionId(),
        request.tier(),
        request.sortOption()
    );
  }
}
