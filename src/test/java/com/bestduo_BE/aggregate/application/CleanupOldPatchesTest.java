package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import com.bestduo_BE.config.AggregateProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CleanupOldPatchesTest {

  @Mock
  private BottomDuoStatAggregateJpaRepository statRepo;

  @Mock
  private BottomDuoMatchupAggregateJpaRepository matchupRepo;

  private CleanupOldPatches cleanupOldPatches;

  @BeforeEach
  void setUp() {
    AggregateProperties props = new AggregateProperties();
    props.getRetention().setPatches(3);
    cleanupOldPatches = new CleanupOldPatches(statRepo, matchupRepo, props);
  }

  @Test
  @DisplayName("execute — patch 5개 중 TOP 3 유지하고 나머지 2개 삭제")
  void executeKeepsTopRetentionPatches() {
    when(statRepo.findAllDistinctPatchVersions())
        .thenReturn(List.of("16.9", "16.8", "16.6", "16.5", "16.4"));
    when(matchupRepo.findAllDistinctPatchVersions())
        .thenReturn(List.of("16.9", "16.8", "16.6"));
    when(statRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(10);
    when(matchupRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(20);

    CleanupOldPatches.Result result = cleanupOldPatches.execute();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(statRepo).deleteByPatchVersionNotIn(captor.capture());
    assertThat(captor.getValue()).containsExactly("16.9", "16.8", "16.6");
    verify(matchupRepo).deleteByPatchVersionNotIn(captor.capture());
    assertThat(captor.getValue()).containsExactly("16.9", "16.8", "16.6");

    assertThat(result.statDeleted()).isEqualTo(10);
    assertThat(result.matchupDeleted()).isEqualTo(20);
    assertThat(result.keepPatches()).containsExactly("16.9", "16.8", "16.6");
  }

  @Test
  @DisplayName("execute — 정수 split 정렬로 \"15.9\"가 \"15.10\"보다 이전 패치로 처리된다")
  void executeSortsByIntegerNotString() {
    when(statRepo.findAllDistinctPatchVersions())
        .thenReturn(List.of("15.9", "15.10", "15.8", "15.7"));
    when(matchupRepo.findAllDistinctPatchVersions()).thenReturn(List.of());
    when(statRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(0);

    cleanupOldPatches.execute();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(statRepo).deleteByPatchVersionNotIn(captor.capture());
    // 문자열 정렬로 \"15.9\" > \"15.10\"이지만 정수 비교 시 \"15.10\" > \"15.9\"
    assertThat(captor.getValue()).containsExactly("15.10", "15.9", "15.8");
  }

  @Test
  @DisplayName("execute — patch 개수가 retention 이하면 delete 호출 안 함")
  void executeSkipsDeleteWhenNoExcess() {
    when(statRepo.findAllDistinctPatchVersions()).thenReturn(List.of("16.9", "16.8"));
    when(matchupRepo.findAllDistinctPatchVersions()).thenReturn(List.of("16.9"));

    CleanupOldPatches.Result result = cleanupOldPatches.execute();

    verify(statRepo, never()).deleteByPatchVersionNotIn(anyList());
    verify(matchupRepo, never()).deleteByPatchVersionNotIn(anyList());
    assertThat(result.statDeleted()).isZero();
    assertThat(result.matchupDeleted()).isZero();
    assertThat(result.keepPatches()).containsExactly("16.9", "16.8");
  }

  @Test
  @DisplayName("execute — 어디에도 patch가 없으면 delete 호출 안 함")
  void executeSkipsWhenNoPatches() {
    when(statRepo.findAllDistinctPatchVersions()).thenReturn(List.of());
    when(matchupRepo.findAllDistinctPatchVersions()).thenReturn(List.of());

    CleanupOldPatches.Result result = cleanupOldPatches.execute();

    verify(statRepo, never()).deleteByPatchVersionNotIn(anyList());
    verify(matchupRepo, never()).deleteByPatchVersionNotIn(anyList());
    assertThat(result.keepPatches()).isEmpty();
  }

  @Test
  @DisplayName("execute — 비표준 patch (\"UNKNOWN\" 등)는 정렬 대상에서 제외")
  void executeFiltersNonStandardPatches() {
    when(statRepo.findAllDistinctPatchVersions())
        .thenReturn(List.of("16.9", "UNKNOWN", "16.8", "broken", "16.6", "16.5"));
    when(matchupRepo.findAllDistinctPatchVersions()).thenReturn(List.of());
    when(statRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(0);

    cleanupOldPatches.execute();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(statRepo).deleteByPatchVersionNotIn(captor.capture());
    assertThat(captor.getValue()).containsExactly("16.9", "16.8", "16.6");
  }

  @Test
  @DisplayName("execute — stat/matchup repository의 patch를 합쳐서 keep 리스트 계산한다")
  void executeUnionsPatchesAcrossRepositories() {
    when(statRepo.findAllDistinctPatchVersions()).thenReturn(List.of("16.9", "16.5"));
    when(matchupRepo.findAllDistinctPatchVersions()).thenReturn(List.of("16.8", "16.6"));
    when(statRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(0);
    when(matchupRepo.deleteByPatchVersionNotIn(anyList())).thenReturn(0);

    CleanupOldPatches.Result result = cleanupOldPatches.execute();

    // 합집합: 16.9, 16.8, 16.6, 16.5 → TOP 3 = 16.9, 16.8, 16.6
    assertThat(result.keepPatches()).containsExactly("16.9", "16.8", "16.6");
  }
}
