package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.SessionRunner;
import com.bestduo_BE.config.DailySessionProperties;
import com.bestduo_BE.infra.persistence.entity.SessionRunLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/session")
public class AdminSessionController {

  private final SessionRunner sessionRunner;
  private final DailySessionProperties dailySessionProperties;

  /**
   * 예:
   * /admin/session/run?budgetTotal=200&seedRatio=0.2&refreshRatio=0.2&consumeLimitPerCycle=10&maxConsumeCycles=20
   * seedRatio, refreshRatio 생략 시 application.yml daily-session 설정 사용
   */
  @PostMapping("/run")
  public SessionRunLog.SessionResult run(
      @RequestParam(defaultValue = "200") int budgetTotal,
      @RequestParam(required = false) Double seedRatioParam,
      @RequestParam(required = false) Double refreshRatioParam,
      @RequestParam(defaultValue = "10") int consumeLimitPerCycle,
      @RequestParam(defaultValue = "20") int maxConsumeCycles
  ) {
    double seedRatio = seedRatioParam != null ? seedRatioParam : dailySessionProperties.getSeedRatio();
    double refreshRatio = refreshRatioParam != null ? refreshRatioParam : dailySessionProperties.getRefreshRatio();
    return sessionRunner.run(budgetTotal, seedRatio, refreshRatio, consumeLimitPerCycle, maxConsumeCycles);
  }
}
