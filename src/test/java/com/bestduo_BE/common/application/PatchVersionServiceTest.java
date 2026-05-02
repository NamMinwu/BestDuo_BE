package com.bestduo_BE.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import com.bestduo_BE.common.infra.persistence.repository.PatchVersionJpaRepository;
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
class PatchVersionServiceTest {

  @Mock
  private PatchVersionJpaRepository patchVersionRepository;

  private PatchVersionService service;

  @BeforeEach
  void setUp() {
    service = new PatchVersionService(patchVersionRepository);
  }

  @Test
  @DisplayName("currentPatchStartTimeEpochSeconds — 패치 있으면 epoch seconds 반환")
  void currentPatchStartTimeEpochSeconds_whenPatchExists_returnsEpochSeconds() {
    OffsetDateTime releasedAt = OffsetDateTime.parse("2025-03-01T00:00:00+00:00");
    PatchVersion patch = PatchVersion.of("15.5", releasedAt);
    given(patchVersionRepository.findTopByOrderByReleasedAtDesc()).willReturn(Optional.of(patch));

    Optional<Long> result = service.currentPatchStartTimeEpochSeconds();

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(releasedAt.toEpochSecond());
  }

  @Test
  @DisplayName("currentPatchStartTimeEpochSeconds — 패치 없으면 empty 반환")
  void currentPatchStartTimeEpochSeconds_whenNoPatch_returnsEmpty() {
    given(patchVersionRepository.findTopByOrderByReleasedAtDesc()).willReturn(Optional.empty());

    Optional<Long> result = service.currentPatchStartTimeEpochSeconds();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("currentPatchVersion — 최신 패치 문자열 반환")
  void currentPatchVersion_whenPatchExists_returnsPatchString() {
    OffsetDateTime releasedAt = OffsetDateTime.parse("2025-03-01T00:00:00+00:00");
    PatchVersion patch = PatchVersion.of("15.5", releasedAt);
    given(patchVersionRepository.findTopByOrderByReleasedAtDesc()).willReturn(Optional.of(patch));

    Optional<String> result = service.currentPatchVersion();

    assertThat(result).contains("15.5");
  }

  @Test
  @DisplayName("currentPatchVersion — 패치 없으면 empty 반환")
  void currentPatchVersion_whenNoPatch_returnsEmpty() {
    given(patchVersionRepository.findTopByOrderByReleasedAtDesc()).willReturn(Optional.empty());

    Optional<String> result = service.currentPatchVersion();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("registerIfAbsent — 신규 패치면 저장 후 true 반환")
  void registerIfAbsent_newPatch_savesAndReturnsTrue() {
    OffsetDateTime releasedAt = OffsetDateTime.parse("2025-04-01T00:00:00+00:00");
    given(patchVersionRepository.existsByPatch("15.8")).willReturn(false);
    given(patchVersionRepository.save(any(PatchVersion.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    boolean result = service.registerIfAbsent("15.8", releasedAt);

    assertThat(result).isTrue();
    ArgumentCaptor<PatchVersion> captor = ArgumentCaptor.forClass(PatchVersion.class);
    verify(patchVersionRepository).save(captor.capture());
    PatchVersion saved = captor.getValue();
    assertThat(saved.getPatch()).isEqualTo("15.8");
    assertThat(saved.getReleasedAt()).isEqualTo(releasedAt);
  }

  @Test
  @DisplayName("registerIfAbsent — 이미 존재하면 저장 안하고 false 반환")
  void registerIfAbsent_existingPatch_skipsAndReturnsFalse() {
    OffsetDateTime releasedAt = OffsetDateTime.parse("2025-04-01T00:00:00+00:00");
    given(patchVersionRepository.existsByPatch("15.8")).willReturn(true);

    boolean result = service.registerIfAbsent("15.8", releasedAt);

    assertThat(result).isFalse();
    verify(patchVersionRepository, never()).save(any());
  }

  // ── resolveEffectivePatchContext ─────────────────────────────────────────

  @Test
  @DisplayName("resolveEffectivePatchContext — 패치가 없으면 empty 반환")
  void resolveEffectivePatchContext_whenNoPatch_returnsEmpty() {
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of());

    Optional<EffectivePatchContext> result = service.resolveEffectivePatchContext();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolveEffectivePatchContext — 최신 패치가 3일 이상 지났으면 정상 컨텍스트 반환")
  void resolveEffectivePatchContext_whenLatestPatchMature_returnsNormalContext() {
    OffsetDateTime releasedAt = OffsetDateTime.now().minusDays(4);
    PatchVersion latest = PatchVersion.of("16.8", releasedAt);
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of(latest));

    Optional<EffectivePatchContext> result = service.resolveEffectivePatchContext();

    assertThat(result).isPresent();
    EffectivePatchContext ctx = result.get();
    assertThat(ctx.patch()).isEqualTo("16.8");
    assertThat(ctx.startTimeEpochSeconds()).isEqualTo(releasedAt.toEpochSecond());
    assertThat(ctx.isInGracePeriod()).isFalse();
  }

  @Test
  @DisplayName("resolveEffectivePatchContext — grace period 중이고 이전 패치 있으면 이전 패치 + endTime 반환")
  void resolveEffectivePatchContext_whenInGracePeriodWithPrevious_returnsPreviousPatchWithEndTime() {
    OffsetDateTime prevReleasedAt = OffsetDateTime.now().minusDays(10);
    OffsetDateTime latestReleasedAt = OffsetDateTime.now().minusDays(1);
    PatchVersion previous = PatchVersion.of("16.7", prevReleasedAt);
    PatchVersion latest = PatchVersion.of("16.8", latestReleasedAt);
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of(latest, previous));

    Optional<EffectivePatchContext> result = service.resolveEffectivePatchContext();

    assertThat(result).isPresent();
    EffectivePatchContext ctx = result.get();
    assertThat(ctx.patch()).isEqualTo("16.7");
    assertThat(ctx.startTimeEpochSeconds()).isEqualTo(prevReleasedAt.toEpochSecond());
    assertThat(ctx.endTimeEpochSeconds()).isEqualTo(latestReleasedAt.toEpochSecond());
    assertThat(ctx.isInGracePeriod()).isTrue();
  }

  @Test
  @DisplayName("resolveEffectivePatchContext — grace period 중이지만 이전 패치 없으면 최신 패치로 fallback")
  void resolveEffectivePatchContext_whenInGracePeriodWithoutPrevious_fallsBackToLatest() {
    OffsetDateTime releasedAt = OffsetDateTime.now().minusDays(1);
    PatchVersion latest = PatchVersion.of("16.8", releasedAt);
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of(latest));

    Optional<EffectivePatchContext> result = service.resolveEffectivePatchContext();

    assertThat(result).isPresent();
    EffectivePatchContext ctx = result.get();
    assertThat(ctx.patch()).isEqualTo("16.8");
    assertThat(ctx.isInGracePeriod()).isFalse();
  }

  // ── recentPatches ────────────────────────────────────────────────────────

  @Test
  @DisplayName("recentPatches — 최신 2개 패치를 최신 → 이전 순으로 반환")
  void recentPatches_whenTwoOrMoreExist_returnsTopTwoOrdered() {
    OffsetDateTime latestAt = OffsetDateTime.parse("2026-04-20T00:00:00+00:00");
    OffsetDateTime previousAt = OffsetDateTime.parse("2026-04-01T00:00:00+00:00");
    PatchVersion latest = PatchVersion.of("16.8", latestAt);
    PatchVersion previous = PatchVersion.of("16.7", previousAt);
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc())
        .willReturn(List.of(latest, previous));

    List<PatchVersion> result = service.recentPatches();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getPatch()).isEqualTo("16.8");
    assertThat(result.get(1).getPatch()).isEqualTo("16.7");
  }

  @Test
  @DisplayName("recentPatches — 패치 1개만 있으면 1개만 반환")
  void recentPatches_whenOnlyOneExists_returnsSingleton() {
    PatchVersion only = PatchVersion.of("16.8", OffsetDateTime.parse("2026-04-20T00:00:00+00:00"));
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of(only));

    List<PatchVersion> result = service.recentPatches();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPatch()).isEqualTo("16.8");
  }

  @Test
  @DisplayName("recentPatches — 패치가 없으면 빈 리스트 반환")
  void recentPatches_whenEmpty_returnsEmptyList() {
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of());

    List<PatchVersion> result = service.recentPatches();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("resolveEffectivePatchContext — 출시 정확히 3일이 지난 시점은 grace period 아님")
  void resolveEffectivePatchContext_whenExactlyThreeDaysOld_notInGracePeriod() {
    OffsetDateTime releasedAt = OffsetDateTime.now().minusDays(3).minusMinutes(1);
    PatchVersion latest = PatchVersion.of("16.8", releasedAt);
    given(patchVersionRepository.findTop2ByOrderByReleasedAtDesc()).willReturn(List.of(latest));

    Optional<EffectivePatchContext> result = service.resolveEffectivePatchContext();

    assertThat(result).isPresent();
    assertThat(result.get().isInGracePeriod()).isFalse();
  }
}
