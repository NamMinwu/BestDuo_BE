package com.bestduo_BE.domain.repository;

import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoStat;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;


public interface BottomDuoStatRepository {
  List<BottomDuoStat> findBy(BottomDuoFilterCriteria criteria);
  void save(List<BottomDuoStat> stats);
}
