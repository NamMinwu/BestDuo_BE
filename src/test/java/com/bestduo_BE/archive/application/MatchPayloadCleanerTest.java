package com.bestduo_BE.archive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.config.ArchiveProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ExtendWith(MockitoExtension.class)
class MatchPayloadCleanerTest {

  private static final String BUCKET = "test-bucket";

  @Mock
  private MatchJpaRepository matchRepository;

  @Mock
  private S3Client s3Client;

  private MatchPayloadCleaner cleaner;

  @BeforeEach
  void setUp() {
    ArchiveProperties props = new ArchiveProperties();
    props.getR2().setBucket(BUCKET);
    cleaner = new MatchPayloadCleaner(matchRepository, s3Client, props);
  }

  @Test
  @DisplayName("execute — 최신 2개 patch 는 protected_latest 로 거부되고 HEAD/DELETE 호출 안 함")
  void protectedLatestPatchesAreRejected() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.8", "16.5"));

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.10", "16.9"), List.of(Tier.MASTER));

    assertThat(result.totalDeleted()).isZero();
    assertThat(result.results()).hasSize(2);
    assertThat(result.results()).allSatisfy(r ->
        assertThat(r.status()).isEqualTo("protected_latest"));
    verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    verify(matchRepository, never()).deleteByTierAndPatch(any(), any());
  }

  @Test
  @DisplayName("execute — HEAD 성공 시 deleteByTierAndPatch 호출되고 status=ok")
  void okFlowDeletesMatchRows() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.5"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());
    when(matchRepository.deleteByTierAndPatch("MASTER", "16.5")).thenReturn(50);

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5"), List.of(Tier.MASTER));

    assertThat(result.totalDeleted()).isEqualTo(50);
    assertThat(result.results()).hasSize(1);
    var r0 = result.results().get(0);
    assertThat(r0.status()).isEqualTo("ok");
    assertThat(r0.patch()).isEqualTo("16.5");
    assertThat(r0.tier()).isEqualTo("MASTER");
    assertThat(r0.objectBytes()).isEqualTo(1024L);
    assertThat(r0.deletedRows()).isEqualTo(50);
    verify(matchRepository).deleteByTierAndPatch("MASTER", "16.5");
  }

  @Test
  @DisplayName("execute — HEAD 가 NoSuchKeyException 이면 no_archive_found, DELETE 호출 안 함")
  void noArchiveFoundWhenHeadReturns404() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.5"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("not found").build());

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5"), List.of(Tier.MASTER));

    assertThat(result.totalDeleted()).isZero();
    assertThat(result.results().get(0).status()).isEqualTo("no_archive_found");
    assertThat(result.results().get(0).objectBytes()).isZero();
    assertThat(result.results().get(0).deletedRows()).isZero();
    verify(matchRepository, never()).deleteByTierAndPatch(any(), any());
  }

  @Test
  @DisplayName("execute — HEAD 가 다른 예외 던지면 head_failed, DELETE 호출 안 함")
  void headFailedOnOtherException() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.5"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(AwsServiceException.builder().message("access denied").build());

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5"), List.of(Tier.MASTER));

    assertThat(result.totalDeleted()).isZero();
    assertThat(result.results().get(0).status()).isEqualTo("head_failed");
    verify(matchRepository, never()).deleteByTierAndPatch(any(), any());
  }

  @Test
  @DisplayName("execute — DELETE 가 예외 던지면 delete_failed, 다음 (patch, tier) 는 계속 진행")
  void deleteFailedDoesNotBlockOthers() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.5", "16.4"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(2048L).build());
    when(matchRepository.deleteByTierAndPatch("MASTER", "16.5"))
        .thenThrow(new RuntimeException("db down"));
    when(matchRepository.deleteByTierAndPatch("MASTER", "16.4")).thenReturn(30);

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5", "16.4"), List.of(Tier.MASTER));

    assertThat(result.results()).hasSize(2);
    assertThat(result.results().get(0).status()).isEqualTo("delete_failed");
    assertThat(result.results().get(0).objectBytes()).isEqualTo(2048L);
    assertThat(result.results().get(0).deletedRows()).isZero();
    assertThat(result.results().get(1).status()).isEqualTo("ok");
    assertThat(result.results().get(1).deletedRows()).isEqualTo(30);
    assertThat(result.totalDeleted()).isEqualTo(30);
  }

  @Test
  @DisplayName("execute — patches × tiers 카르테시안 곱으로 모든 조합 반환")
  void cartesianProductOverPatchesAndTiers() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "16.9", "16.5", "16.4"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(100L).build());
    when(matchRepository.deleteByTierAndPatch(any(), any())).thenReturn(10);

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5", "16.4"),
        List.of(Tier.MASTER, Tier.EMERALD));

    assertThat(result.results()).hasSize(4);
    assertThat(result.totalDeleted()).isEqualTo(40);
    verify(s3Client, times(4)).headObject(any(HeadObjectRequest.class));
    verify(matchRepository, times(4)).deleteByTierAndPatch(any(), any());
  }

  @Test
  @DisplayName("execute — 정수 split 정렬로 \"15.10\" 이 \"15.9\" 보다 최신으로 처리된다")
  void protectedSortByIntegerNotString() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("15.9", "15.10", "15.8", "15.7"));

    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("15.10", "15.9"), List.of(Tier.MASTER));

    assertThat(result.results()).allSatisfy(r ->
        assertThat(r.status()).isEqualTo("protected_latest"));
    verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
  }

  @Test
  @DisplayName("execute — 비표준 patch (\"UNKNOWN\" 등) 는 protected 계산에서 제외")
  void protectedListIgnoresNonStandardPatches() {
    when(matchRepository.findDistinctPatches())
        .thenReturn(List.of("16.10", "UNKNOWN", "16.9", "broken", "16.5"));
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(50L).build());
    when(matchRepository.deleteByTierAndPatch(any(), any())).thenReturn(5);

    // protectedLatest = [16.10, 16.9] — UNKNOWN/broken 은 정렬 대상 아님
    MatchPayloadCleaner.Result result = cleaner.execute(
        List.of("16.5"), List.of(Tier.MASTER));

    assertThat(result.results().get(0).status()).isEqualTo("ok");
  }
}
