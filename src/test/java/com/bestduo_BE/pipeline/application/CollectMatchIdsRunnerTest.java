package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.IngestMatchDetail;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class CollectMatchIdsRunnerTest {

  @Mock private SummonerJpaRepository summonerRepository;
  @Mock private RiotApiPort riotApiPort;
  @Mock private MatchJpaRepository matchRepository;
  @Mock private IngestMatchDetail ingestMatchDetail;
  @Mock private PatchVersionService patchVersionService;
  @Mock private DailyBudgetTracker budgetTracker;

  private PipelineProperties props;
  private SimpleMeterRegistry registry;
  private CollectMatchIdsRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setCollectBatchSize(3);
    PipelineProperties.TierMatchCount tierMatchCount = new PipelineProperties.TierMatchCount();
    tierMatchCount.setApexTiers(30);
    tierMatchCount.setDiamondEmerald(10);
    props.setTierMatchCount(tierMatchCount);
    registry = new SimpleMeterRegistry();
    runner = new CollectMatchIdsRunner(
        summonerRepository, riotApiPort, matchRepository, ingestMatchDetail,
        patchVersionService, budgetTracker, props, new PipelineMetrics(registry));
  }

  private EffectivePatchContext normalCtx() {
    return new EffectivePatchContext("15.23", 1700000000L, null);
  }

  // ── hasPending ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("collect 예산 소진 시 hasPending는 false")
  void hasPending_whenBudgetExhausted_returnsFalse() {
    given(budgetTracker.canCollect()).willReturn(false);

    assertThat(runner.hasPending()).isFalse();
  }

  @Test
  @DisplayName("patch context가 없으면 hasPending는 false (조건①)")
  void hasPending_whenNoPatchContext_returnsFalse() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());

    assertThat(runner.hasPending()).isFalse();
  }

  @Test
  @DisplayName("대기 중인 summoner가 없으면 hasPending는 false")
  void hasPending_whenNoSummoners_returnsFalse() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(1)).willReturn(List.of());

    assertThat(runner.hasPending()).isFalse();
  }

  @Test
  @DisplayName("예산·patch·대기 summoner 모두 있으면 hasPending는 true")
  void hasPending_whenAllConditionsMet_returnsTrue() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(1))
        .willReturn(List.of(summonerWithTier("p1", Tier.DIAMOND)));

    assertThat(runner.hasPending()).isTrue();
  }

  // ── runBatch ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("예산 소진 시 runBatch는 BUDGET_EXHAUSTED 반환")
  void runBatch_whenBudgetExhausted_returnsBudgetExhausted() {
    given(budgetTracker.canCollect()).willReturn(false);

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    assertThat(result.type()).isEqualTo(CollectMatchIdsRunner.BatchResult.Type.BUDGET_EXHAUSTED);
    verify(summonerRepository, never()).findMatchIdsPendingSummoners(anyInt());
  }

  @Test
  @DisplayName("patch context가 없으면 runBatch는 수집하지 않고 NO_PENDING 반환 (조건①)")
  void runBatch_whenNoPatchContext_returnsNoPending() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    assertThat(result.type()).isEqualTo(CollectMatchIdsRunner.BatchResult.Type.NO_PENDING);
    verify(summonerRepository, never()).findMatchIdsPendingSummoners(anyInt());
    verify(riotApiPort, never()).findRecentMatchIds(any(), anyInt());
  }

  @Test
  @DisplayName("대기 summoner 없으면 NO_PENDING 반환")
  void runBatch_whenNoPending_returnsNoPending() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of());

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    assertThat(result.type()).isEqualTo(CollectMatchIdsRunner.BatchResult.Type.NO_PENDING);
  }

  @Test
  @DisplayName("정상 패치: 각 matchId를 inline ingest하고 collectedAt·예산을 갱신한다")
  void runBatch_normalPatch_ingestsEachMatchAndMarksCollected() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince("p-dia", 1700000000L, 10))
        .willReturn(List.of("m1", "m2"));

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    verify(ingestMatchDetail).execute("m1", Tier.DIAMOND, "15.23");
    verify(ingestMatchDetail).execute("m2", Tier.DIAMOND, "15.23");
    verify(summonerRepository).markMatchIdsCollected(eq("p-dia"), any(OffsetDateTime.class));
    verify(budgetTracker).recordCollectCall(1);
    assertThat(result.matchIdsQueued()).isEqualTo(2);
    assertThat(registry.counter("pipeline.ingest.success").count()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("CHALLENGER tier는 apexTiers matchCount(30)를 사용한다")
  void runBatch_apexTier_usesApexMatchCount() {
    Summoner summoner = summonerWithTier("p-chall", Tier.CHALLENGER);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince("p-chall", 1700000000L, 30)).willReturn(List.of("m1"));

    runner.runBatch();

    verify(riotApiPort).findMatchIdsSince("p-chall", 1700000000L, 30);
  }

  @Test
  @DisplayName("grace period 중: findMatchIdsBetween으로 수집하고 이전 패치로 ingest한다")
  void runBatch_inGracePeriod_usesFindMatchIdsBetween() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    EffectivePatchContext ctx = new EffectivePatchContext("16.7", 1699000000L, 1700000000L);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsBetween("p-dia", 1699000000L, 1700000000L, 10))
        .willReturn(List.of("m1"));

    runner.runBatch();

    verify(riotApiPort).findMatchIdsBetween("p-dia", 1699000000L, 1700000000L, 10);
    verify(riotApiPort, never()).findMatchIdsSince(any(), anyLong(), anyInt());
    verify(ingestMatchDetail).execute("m1", Tier.DIAMOND, "16.7");
  }

  @Test
  @DisplayName("이미 수집된 매치(existsById=true)는 ingest를 건너뛴다 (dedup)")
  void runBatch_dedup_skipsAlreadyIngestedMatch() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince("p-dia", 1700000000L, 10))
        .willReturn(List.of("m1", "m2"));
    given(matchRepository.existsById("m1")).willReturn(true);

    runner.runBatch();

    verify(ingestMatchDetail, never()).execute(eq("m1"), any(), any());
    verify(ingestMatchDetail).execute("m2", Tier.DIAMOND, "15.23");
    assertThat(registry.counter("pipeline.ingest.success").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("개별 ingest 실패(옵션 A): metric 기록 후 skip하고 summoner는 collected 처리한다")
  void runBatch_ingestFailure_skipsAndStillMarksCollected() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince("p-dia", 1700000000L, 10)).willReturn(List.of("m1"));
    given(ingestMatchDetail.execute("m1", Tier.DIAMOND, "15.23"))
        .willThrow(new RuntimeException("ingest boom"));

    runner.runBatch();

    // 옵션 A: 실패해도 summoner는 collected 처리(재수집 시 dedup), failure metric 기록
    verify(summonerRepository).markMatchIdsCollected(eq("p-dia"), any(OffsetDateTime.class));
    assertThat(registry.counter("pipeline.ingest.failure", "reason", "other").count())
        .isEqualTo(1.0);
    assertThat(registry.counter("pipeline.ingest.success").count()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("수집(matchIds 조회) 실패 시 summoner는 미수집·예산 미차감으로 남는다")
  void runBatch_collectionError_doesNotMarkCollectedNorChargeBudget() {
    Summoner summoner = summonerWithTier("p-bad", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince(eq("p-bad"), anyLong(), anyInt()))
        .willThrow(new RuntimeException("collect error"));

    runner.runBatch();

    verify(summonerRepository, never()).markMatchIdsCollected(eq("p-bad"), any());
    verify(budgetTracker, never()).recordCollectCall(anyInt());
  }

  @Test
  @DisplayName("429 예외는 전파된다")
  void runBatch_propagatesRateLimitedException() {
    Summoner summoner = summonerWithTier("p-1", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince(any(), anyLong(), anyInt()))
        .willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.runBatch())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("matchIds가 비어있으면 ingest하지 않지만 collectedAt은 갱신한다")
  void runBatch_whenNoMatchIds_doesNotIngestButMarksCollected() {
    Summoner summoner = summonerWithTier("p-empty", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince(any(), anyLong(), anyInt())).willReturn(List.of());

    runner.runBatch();

    verify(ingestMatchDetail, never()).execute(any(), any(), any());
    verify(summonerRepository).markMatchIdsCollected(eq("p-empty"), any(OffsetDateTime.class));
  }

  // ── ingest 실패 분류 (classifyIngestFailure) ──────────────────────────────

  @ParameterizedTest
  @CsvSource({"401,auth", "403,auth", "404,not_found", "400,client_4xx"})
  @DisplayName("HttpClientErrorException은 상태코드에 따라 auth/not_found/client_4xx로 분류된다")
  void runBatch_ingestFailure_classifiesHttpClientError(int status, String expectedReason) {
    runBatchWithIngestThrowing(new HttpClientErrorException(HttpStatus.valueOf(status)));

    assertThat(registry.counter("pipeline.ingest.failure", "reason", expectedReason).count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("HttpServerErrorException(5xx)은 server_5xx로 분류된다")
  void runBatch_ingestFailure_classifiesServerErrorAs5xx() {
    runBatchWithIngestThrowing(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThat(registry.counter("pipeline.ingest.failure", "reason", "server_5xx").count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("ResourceAccessException(timeout/IO)은 timeout으로 분류된다")
  void runBatch_ingestFailure_classifiesResourceAccessAsTimeout() {
    runBatchWithIngestThrowing(new ResourceAccessException("connection timed out"));

    assertThat(registry.counter("pipeline.ingest.failure", "reason", "timeout").count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("RiotApiException은 원인(cause)을 풀어 분류한다 (401 wrap → auth)")
  void runBatch_ingestFailure_unwrapsRiotApiExceptionCause() {
    runBatchWithIngestThrowing(
        new RiotApiException("load failed", new HttpClientErrorException(HttpStatus.UNAUTHORIZED)));

    assertThat(registry.counter("pipeline.ingest.failure", "reason", "auth").count())
        .isEqualTo(1.0);
  }

  /** 단일 summoner·단일 매치에서 ingest 가 주어진 예외를 던지도록 세팅 후 runBatch 실행. */
  private void runBatchWithIngestThrowing(Throwable ingestException) {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    given(budgetTracker.canCollect()).willReturn(true);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(normalCtx()));
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(riotApiPort.findMatchIdsSince("p-dia", 1700000000L, 10)).willReturn(List.of("m1"));
    given(ingestMatchDetail.execute("m1", Tier.DIAMOND, "15.23")).willThrow(ingestException);

    runner.runBatch();
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private Summoner summonerWithTier(String puuid, Tier tier) {
    return Summoner.builder()
        .puuid(puuid)
        .lastKnownTier(tier)
        .leagueEntryFetchedAt(OffsetDateTime.now().minusHours(1))
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }
}
