package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import org.springframework.stereotype.Component;

public interface RiotMatchLoader {
  RiotMatchDto loadMatch(String matchId);
}
