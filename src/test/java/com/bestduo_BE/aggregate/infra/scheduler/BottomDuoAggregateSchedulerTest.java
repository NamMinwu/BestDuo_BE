package com.bestduo_BE.aggregate.infra.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoFromMatch;
import com.bestduo_BE.aggregate.application.CleanupOldPatches;
import com.bestduo_BE.aggregate.application.ComputeBottomDuoRanking;
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
  private AggregateBottomDuoFromMatch fromMatchUseCase;

  @Mock
  private ComputeBottomDuoRanking rankingUseCase;

  @Mock
  private CleanupOldPatches cleanupUseCase;

  private BottomDuoAggregateScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new BottomDuoAggregateScheduler(
        patchVersionService, fromMatchUseCase, rankingUseCase, cleanupUseCase);
  }

  @Test
  @DisplayName("run — 등록된 patch가 없으면 fromMatch/ranking/cleanup 모두 호출하지 않는다")
  void runSkipsWhenNoPatchRegistered() {
    when(patchVersionService.currentPatch()).thenReturn(Optional.empty());

    scheduler.run();

    verifyNoInteractions(fromMatchUseCase);
    verifyNoInteractions(rankingUseCase);
    verifyNoInteractions(cleanupUseCase);
  }

  @Test
  @DisplayName("run — CHALLENGER~EMERALD 5개 tier 에 대해 fromMatch → ranking 순으로 호출 후 cleanup 을 호출한다")
  void runInvokesFromMatchThenRankingForAllFiveTiersThenCleanup() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(fromMatchUseCase.execute(eq(PATCH), any(Tier.class), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(10, 5, 5, 5, 5, 10L, 10L));
    when(rankingUseCase.execute(eq(PATCH), any(String.class)))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 5));
    when(cleanupUseCase.execute())
        .thenReturn(new CleanupOldPatches.Result(1, 2, List.of(PATCH)));

    scheduler.run();

    ArgumentCaptor<Tier> tierCaptor = ArgumentCaptor.forClass(Tier.class);
    verify(fromMatchUseCase, times(5)).execute(eq(PATCH), tierCaptor.capture(), eq(true));
    assertThat(tierCaptor.getAllValues()).containsExactlyElementsOf(EXPECTED_TIERS);

    ArgumentCaptor<String> rankingTierCaptor = ArgumentCaptor.forClass(String.class);
    verify(rankingUseCase, times(5)).execute(eq(PATCH), rankingTierCaptor.capture());
    assertThat(rankingTierCaptor.getAllValues())
        .containsExactlyElementsOf(EXPECTED_TIERS.stream().map(Tier::name).toList());

    verify(cleanupUseCase).execute();
  }

  @Test
  @DisplayName("run — fromMatch 가 예외를 던진 tier 는 ranking 을 건너뛰고 다음 tier 로 진행한다")
  void runSkipsRankingWhenFromMatchFails() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(fromMatchUseCase.execute(eq(PATCH), eq(Tier.CHALLENGER), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(1, 1, 1, 1, 1, 1L, 1L));
    when(fromMatchUseCase.execute(eq(PATCH), eq(Tier.GRANDMASTER), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(2, 2, 2, 2, 2, 2L, 2L));
    when(fromMatchUseCase.execute(eq(PATCH), eq(Tier.MASTER), eq(true)))
        .thenThrow(new RuntimeException("simulated extract error"));
    when(fromMatchUseCase.execute(eq(PATCH), eq(Tier.DIAMOND), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(3, 3, 3, 3, 3, 3L, 3L));
    when(fromMatchUseCase.execute(eq(PATCH), eq(Tier.EMERALD), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(4, 4, 4, 4, 4, 4L, 4L));
    when(rankingUseCase.execute(eq(PATCH), any(String.class)))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(cleanupUseCase.execute())
        .thenReturn(new CleanupOldPatches.Result(0, 0, List.of(PATCH)));

    scheduler.run();

    verify(rankingUseCase).execute(PATCH, Tier.CHALLENGER.name());
    verify(rankingUseCase).execute(PATCH, Tier.GRANDMASTER.name());
    verify(rankingUseCase, never()).execute(PATCH, Tier.MASTER.name());
    verify(rankingUseCase).execute(PATCH, Tier.DIAMOND.name());
    verify(rankingUseCase).execute(PATCH, Tier.EMERALD.name());
    verify(cleanupUseCase).execute();
  }

  @Test
  @DisplayName("run — ranking 에서 예외가 발생해도 나머지 tier 와 cleanup 은 계속 진행한다")
  void runContinuesAfterRankingFailure() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(fromMatchUseCase.execute(eq(PATCH), any(Tier.class), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(1, 1, 1, 1, 1, 1L, 1L));
    when(rankingUseCase.execute(eq(PATCH), eq(Tier.MASTER.name())))
        .thenThrow(new RuntimeException("simulated ranking error"));
    when(rankingUseCase.execute(eq(PATCH), eq(Tier.CHALLENGER.name())))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(rankingUseCase.execute(eq(PATCH), eq(Tier.GRANDMASTER.name())))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(rankingUseCase.execute(eq(PATCH), eq(Tier.DIAMOND.name())))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(rankingUseCase.execute(eq(PATCH), eq(Tier.EMERALD.name())))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(cleanupUseCase.execute())
        .thenReturn(new CleanupOldPatches.Result(0, 0, List.of(PATCH)));

    scheduler.run();

    verify(fromMatchUseCase, times(5)).execute(eq(PATCH), any(Tier.class), eq(true));
    verify(rankingUseCase, times(5)).execute(eq(PATCH), any(String.class));
    verify(cleanupUseCase).execute();
  }

  @Test
  @DisplayName("run — cleanup 호출에서 예외가 발생해도 스케줄러는 정상 종료한다")
  void runSwallowsCleanupFailure() {
    when(patchVersionService.currentPatch())
        .thenReturn(Optional.of(PatchVersion.of(PATCH, OffsetDateTime.now())));
    when(fromMatchUseCase.execute(eq(PATCH), any(Tier.class), eq(true)))
        .thenReturn(new AggregateBottomDuoFromMatch.Result(1, 1, 1, 1, 1, 1L, 1L));
    when(rankingUseCase.execute(eq(PATCH), any(String.class)))
        .thenReturn(new ComputeBottomDuoRanking.Result(PATCH, 1));
    when(cleanupUseCase.execute())
        .thenThrow(new RuntimeException("simulated cleanup error"));

    scheduler.run();

    verify(cleanupUseCase).execute();
  }
}
