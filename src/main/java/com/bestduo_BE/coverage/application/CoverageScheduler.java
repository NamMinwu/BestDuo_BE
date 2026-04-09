package com.bestduo_BE.coverage.application;

import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CoverageScheduler {

  private final CoverageSchedulingService coverageSchedulingService;

  @Scheduled(fixedDelayString = "${work-item.scheduler-fixed-delay-ms:30000}")
  public void scheduledRun() {
    schedule();
  }

  public ScheduleResult schedule() {
    return coverageSchedulingService.schedule();
  }

  public record ScheduleResult(int createdCount, List<WorkItem> workItems) {
  }
}
