package com.bestduo_BE.aggregate.infra.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoMatchup;
import com.bestduo_BE.aggregate.application.AggregateBottomDuoStats;
import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BottomDuoAggregateSchedulerTest {

  private static final String PATCH = "15.23";
  private static final List<Tier> EXPECTED_TIERS = List.of(
      Tier.CHALLENGER,
      Tier.GRANDMASTER,
      Tier.MASTER,
      Tier.DIAMOND,
      Tier.EMERALD
  );

  @Mock
  private PatchVersionService patchVersionService;

  @Mock
  private AggregateBottomDuoStats statUseCase;

  @Mock
  private AggregateBottomDuoMatchup matchupUseCase;

  private BottomDuoAggregateScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new BottomDuoAggregateScheduler(patchVersionService, statUseCase, matchupUseCase);
  }

  @Test
  @DisplayName("run — 등록된 patch가 없으면 stat/matchup 모두 호출하지 않는다")
  void runSkipsWhenNoPatchRegistered() {
    when(patchVersionService.currentPatch()).thenReturn(Optional.empty());

    scheduler.run();

    verifyNoInteractions(statUseCase);
    verifyNoInteractions(matchupUseCase);
  }

  @Test
  @DisplayName("run — CHALLENGER~EMERALD 5개 tier로 stat을 순차 호출하고 마지막에 matchup을 1회 호출한다")
  void runInvokesAllFiveTiersThenMatchup() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(statUseCase.execute(eq(PATCH), any(Tier.class)))
        .thenReturn(new AggregateBottomDuoStats.Result(10, 5));
    when(matchupUseCase.execute())
        .thenReturn(new AggregateBottomDuoMatchup.Result(20));

    scheduler.run();

    ArgumentCaptor<Tier> tierCaptor = ArgumentCaptor.forClass(Tier.class);
    verify(statUseCase, times(5)).execute(eq(PATCH), tierCaptor.capture());
    assertThat(tierCaptor.getAllValues()).containsExactlyElementsOf(EXPECTED_TIERS);
    verify(matchupUseCase).execute();
  }

  @Test
  @DisplayName("run — 중간 tier에서 예외가 발생해도 나머지 tier와 matchup은 계속 진행한다")
  void runContinuesAfterTierFailure() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(statUseCase.execute(eq(PATCH), eq(Tier.CHALLENGER)))
        .thenReturn(new AggregateBottomDuoStats.Result(1, 1));
    when(statUseCase.execute(eq(PATCH), eq(Tier.GRANDMASTER)))
        .thenReturn(new AggregateBottomDuoStats.Result(2, 2));
    when(statUseCase.execute(eq(PATCH), eq(Tier.MASTER)))
        .thenThrow(new RuntimeException("simulated DB error"));
    when(statUseCase.execute(eq(PATCH), eq(Tier.DIAMOND)))
        .thenReturn(new AggregateBottomDuoStats.Result(3, 3));
    when(statUseCase.execute(eq(PATCH), eq(Tier.EMERALD)))
        .thenReturn(new AggregateBottomDuoStats.Result(4, 4));
    when(matchupUseCase.execute())
        .thenReturn(new AggregateBottomDuoMatchup.Result(50));

    scheduler.run();

    verify(statUseCase).execute(PATCH, Tier.CHALLENGER);
    verify(statUseCase).execute(PATCH, Tier.GRANDMASTER);
    verify(statUseCase).execute(PATCH, Tier.MASTER);
    verify(statUseCase).execute(PATCH, Tier.DIAMOND);
    verify(statUseCase).execute(PATCH, Tier.EMERALD);
    verify(matchupUseCase).execute();
  }

  @Test
  @DisplayName("run — matchup 호출에서 예외가 발생해도 스케줄러는 정상 종료한다")
  void runSwallowsMatchupFailure() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(statUseCase.execute(eq(PATCH), any(Tier.class)))
        .thenReturn(new AggregateBottomDuoStats.Result(1, 1));
    when(matchupUseCase.execute())
        .thenThrow(new RuntimeException("simulated matchup error"));

    scheduler.run();

    verify(matchupUseCase).execute();
    verify(statUseCase, never()).execute(PATCH, Tier.ALL_TIERS);
  }
}
