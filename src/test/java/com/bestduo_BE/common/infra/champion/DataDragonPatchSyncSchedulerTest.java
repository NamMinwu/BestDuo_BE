package com.bestduo_BE.common.infra.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bestduo_BE.common.application.PatchVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class DataDragonPatchSyncSchedulerTest {

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private PatchVersionService patchVersionService;

  private DataDragonPatchSyncScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new DataDragonPatchSyncScheduler(restTemplate, patchVersionService);
  }

  // ─── normalizePatch 단위 테스트 ───────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({
      "15.23.1,   15.23",
      "15.23.456.7890, 15.23",
      "14.1.0,    14.1",
      "1.2.3,     1.2"
  })
  @DisplayName("normalizePatch — major.minor만 추출")
  void normalizePatch_returnsNormalizedVersion(String fullVersion, String expected) {
    assertThat(DataDragonPatchSyncScheduler.normalizePatch(fullVersion.trim()))
        .isEqualTo(expected.trim());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("normalizePatch — null 또는 빈 문자열이면 null 반환")
  void normalizePatch_nullOrBlank_returnsNull(String input) {
    assertThat(DataDragonPatchSyncScheduler.normalizePatch(input)).isNull();
  }

  @Test
  @DisplayName("normalizePatch — 점이 하나뿐인 입력은 null 반환")
  void normalizePatch_singleSegment_returnsNull() {
    assertThat(DataDragonPatchSyncScheduler.normalizePatch("15")).isNull();
  }

  // ─── syncLatestPatch 행동 테스트 ─────────────────────────────────────────

  @Test
  @DisplayName("syncLatestPatch — 신규 패치 감지 시 registerIfAbsent 호출")
  void syncLatestPatch_newPatch_registersIt() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willReturn(new String[]{"15.24.1", "15.23.1", "15.22.1"});
    given(patchVersionService.registerIfAbsent(eq("15.24"), any())).willReturn(true);

    scheduler.syncLatestPatch();

    then(patchVersionService).should().registerIfAbsent(eq("15.24"), any());
  }

  @Test
  @DisplayName("syncLatestPatch — 이미 등록된 패치면 registerIfAbsent만 호출하고 로그만 남김")
  void syncLatestPatch_existingPatch_callsRegisterButSkips() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willReturn(new String[]{"15.23.1"});
    given(patchVersionService.registerIfAbsent(eq("15.23"), any())).willReturn(false);

    scheduler.syncLatestPatch();

    then(patchVersionService).should().registerIfAbsent(eq("15.23"), any());
  }

  @Test
  @DisplayName("syncLatestPatch — versions.json이 빈 배열이면 registerIfAbsent 호출 안 함")
  void syncLatestPatch_emptyVersions_doesNotRegister() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willReturn(new String[]{});

    scheduler.syncLatestPatch();

    then(patchVersionService).should(never()).registerIfAbsent(any(), any());
  }

  @Test
  @DisplayName("syncLatestPatch — versions.json이 null이면 registerIfAbsent 호출 안 함")
  void syncLatestPatch_nullVersions_doesNotRegister() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willReturn(null);

    scheduler.syncLatestPatch();

    then(patchVersionService).should(never()).registerIfAbsent(any(), any());
  }

  @Test
  @DisplayName("syncLatestPatch — Data Dragon API 오류 시 예외 전파 안 함")
  void syncLatestPatch_apiFailure_doesNotPropagate() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willThrow(new RestClientException("connection refused"));

    assertThatCode(() -> scheduler.syncLatestPatch()).doesNotThrowAnyException();
    then(patchVersionService).should(never()).registerIfAbsent(any(), any());
  }

  @Test
  @DisplayName("syncLatestPatch — 정규화 실패하는 버전 문자열은 registerIfAbsent 호출 안 함")
  void syncLatestPatch_unnormalizableVersion_doesNotRegister() {
    given(restTemplate.getForObject(any(String.class), eq(String[].class)))
        .willReturn(new String[]{"badversion"});

    scheduler.syncLatestPatch();

    then(patchVersionService).should(never()).registerIfAbsent(any(), any());
  }
}
