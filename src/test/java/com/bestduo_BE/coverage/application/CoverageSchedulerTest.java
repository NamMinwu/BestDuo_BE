package com.bestduo_BE.coverage.application;

import static org.mockito.BDDMockito.then;

import com.bestduo_BE.coverage.presentation.api.CoverageScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageSchedulerTest {

  @Mock
  private CoverageSchedulingService coverageSchedulingService;

  private CoverageScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new CoverageScheduler(coverageSchedulingService);
  }

  @Test
  void scheduleDelegatesToTransactionalService() {
    scheduler.schedule();

    then(coverageSchedulingService).should().schedule();
  }

  @Test
  void scheduledRunDelegatesToTransactionalService() {
    scheduler.scheduledRun();

    then(coverageSchedulingService).should().schedule();
  }
}
