package com.bestduo_BE.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import com.bestduo_BE.common.infra.persistence.repository.PatchVersionJpaRepository;
import java.time.OffsetDateTime;
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
}
