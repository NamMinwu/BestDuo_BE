package com.bestduo_BE.domain.repository;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import java.util.List;

public interface BottomDuoMatchupStatRepository {
  // 최소 개임 수 필터링 + 정렬까지
  List<BottomDuoMatchupStat> findBy(BottomDuoFilterCriteria criteria);
}
