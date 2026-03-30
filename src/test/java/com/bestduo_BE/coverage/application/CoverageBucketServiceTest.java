package com.bestduo_BE.coverage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.application.exception.CoverageBucketAlreadyExistsException;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.domain.model.CoverageBucketStatus;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketCreateRequest;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CoverageBucketServiceTest {

  @Mock
  private CoverageBucketJpaRepository coverageBucketRepository;

  @Mock
  private CoverageBucketCountJpaRepository coverageBucketCountRepository;

  private CoverageBucketService service;

  @BeforeEach
  void setUp() {
    service = new CoverageBucketService(coverageBucketRepository, coverageBucketCountRepository);
  }

  @Test
  void createUsesCurrentCoverageCountAndDefaultPriority() {
    CoverageBucketCreateRequest request = new CoverageBucketCreateRequest("15.7", Tier.MASTER, 20_000L, null);
    given(coverageBucketRepository.existsByPatchAndTier("15.7", Tier.MASTER)).willReturn(false);
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(3_400L);
    given(coverageBucketRepository.save(org.mockito.ArgumentMatchers.any(CoverageBucket.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageBucketResponse response = service.create(request);

    ArgumentCaptor<CoverageBucket> captor = ArgumentCaptor.forClass(CoverageBucket.class);
    verify(coverageBucketRepository).save(captor.capture());
    CoverageBucket saved = captor.getValue();
    assertThat(saved.getPatch()).isEqualTo("15.7");
    assertThat(saved.getTier()).isEqualTo(Tier.MASTER);
    assertThat(saved.getTargetMatchCount()).isEqualTo(20_000L);
    assertThat(saved.getCurrentMatchCount()).isEqualTo(3_400L);
    assertThat(saved.getStatus()).isEqualTo(CoverageBucketStatus.COLLECTING);
    assertThat(saved.getPriority()).isEqualTo(CoverageBucketService.DEFAULT_PRIORITY);

    assertThat(response.patch()).isEqualTo("15.7");
    assertThat(response.currentMatchCount()).isEqualTo(3_400L);
    assertThat(response.deficitMatchCount()).isEqualTo(16_600L);
    assertThat(response.status()).isEqualTo("COLLECTING");
    assertThat(response.lastEvaluatedAt()).isNotNull();
  }

  @Test
  void createRejectsDuplicatePatchTierBucket() {
    CoverageBucketCreateRequest request = new CoverageBucketCreateRequest("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketRepository.existsByPatchAndTier("15.7", Tier.MASTER)).willReturn(true);

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(CoverageBucketAlreadyExistsException.class)
        .hasMessageContaining("Coverage bucket already exists");
  }

  @Test
  void createTranslatesUniqueConstraintViolationToConflictStyleException() {
    CoverageBucketCreateRequest request = new CoverageBucketCreateRequest("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketRepository.existsByPatchAndTier("15.7", Tier.MASTER)).willReturn(false);
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(0L);
    given(coverageBucketRepository.save(org.mockito.ArgumentMatchers.any(CoverageBucket.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(CoverageBucketAlreadyExistsException.class)
        .hasMessageContaining("Coverage bucket already exists");
  }

  @Test
  void getThrowsCoverageBucketNotFoundExceptionWhenMissing() {
    given(coverageBucketRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(99L))
        .isInstanceOf(CoverageBucketNotFoundException.class)
        .hasMessageContaining("Coverage bucket not found");
  }

  @Test
  void getReevaluatesCurrentCountAndMarksBucketSufficient() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 2_000L, 5);
    given(coverageBucketRepository.findById(7L)).willReturn(Optional.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(2_500L);

    CoverageBucketResponse response = service.get(7L);

    assertThat(response.currentMatchCount()).isEqualTo(2_500L);
    assertThat(response.deficitMatchCount()).isZero();
    assertThat(response.status()).isEqualTo("SUFFICIENT");
  }

  @Test
  void getAllReturnsBucketsOrderedByPriorityAndId() {
    CoverageBucket first = CoverageBucket.create("15.7", Tier.MASTER, 100L, 1);
    CoverageBucket second = CoverageBucket.create("15.7", Tier.DIAMOND, 100L, 2);
    given(coverageBucketRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(first, second));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(10L);
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "DIAMOND")).willReturn(20L);

    List<CoverageBucketResponse> responses = service.getAll();

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).tier()).isEqualTo(Tier.MASTER);
    assertThat(responses.get(0).currentMatchCount()).isEqualTo(10L);
    assertThat(responses.get(1).tier()).isEqualTo(Tier.DIAMOND);
    assertThat(responses.get(1).currentMatchCount()).isEqualTo(20L);
  }
}
