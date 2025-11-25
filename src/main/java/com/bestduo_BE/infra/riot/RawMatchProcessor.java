package com.bestduo_BE.infra.riot;

import com.bestduo_BE.domain.model.BottomDuoMatch;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RawMatchProcessor {
  public List<BottomDuoMatch> getMatches(RiotMatchDto matchDto) {
    return null;
  }
}
