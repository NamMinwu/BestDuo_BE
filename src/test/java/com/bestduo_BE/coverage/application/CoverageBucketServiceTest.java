package com.bestduo_BE.coverage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.domain.model.CoverageBucketStatus;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  @DisplayName("존재하지 않는 버킷 조회 시 CoverageBucketNotFoundException 발생")
  void getThrowsCoverageBucketNotFoundExceptionWhenMissing() {
    given(coverageBucketRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(99L))
        .isInstanceOf(CoverageBucketNotFoundException.class)
        .hasMessageContaining("Coverage bucket not found");
  }

  @Test
  @DisplayName("버킷 조회 시 현재 매치 수를 재평가하고 SUFFICIENT 상태로 변경")
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
  @DisplayName("전체 버킷 조회 시 priority ASC, id ASC 순서로 반환")
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
