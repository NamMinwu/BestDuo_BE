package com.bestduo_BE.application;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import com.bestduo_BE.application.filter.BottomDuoFilterParser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewBottomDuoStatistics {
  private final BottomDuoStatRepository bottomDuoStatRepository;
  private final BottomDuoFilterParser bottomDuoFilterParser;

  public BottomDuoStatisticsResponse handle(BottomDuoStatisticsRequest request) {
    BottomDuoFilterCriteria criteria = bottomDuoFilterParser.parse(request);
    List<BottomDuoStat> stats = bottomDuoStatRepository.findBy(criteria);
    return BottomDuoStatisticsResponse.from(stats);
  }
}
