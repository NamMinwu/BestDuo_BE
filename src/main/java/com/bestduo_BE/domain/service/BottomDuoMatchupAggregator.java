package com.bestduo_BE.domain.service;

import com.bestduo_BE.domain.model.BottomDuoMatch;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BottomDuoMatchupAggregator {

  public List<BottomDuoMatchupStat> calculate(List<BottomDuoMatch> matches) {
    return List.of();
  }
}
