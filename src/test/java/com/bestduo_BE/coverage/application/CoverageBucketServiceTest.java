package com.bestduo_BE.coverage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
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

  private CoverageBucketService service;

  @BeforeEach
  void setUp() {
    service = new CoverageBucketService(coverageBucketRepository);
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
  @DisplayName("버킷 단건 조회 시 id·patch·tier를 반환한다")
  void getReturnsBucketResponse() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER);
    given(coverageBucketRepository.findById(7L)).willReturn(Optional.of(bucket));

    CoverageBucketResponse response = service.get(7L);

    assertThat(response.patch()).isEqualTo("15.7");
    assertThat(response.tier()).isEqualTo(Tier.MASTER);
  }

  @Test
  @DisplayName("전체 버킷 조회 시 id ASC 순서로 반환한다")
  void getAllReturnsBucketsOrderedById() {
    CoverageBucket first = CoverageBucket.create("15.7", Tier.MASTER);
    CoverageBucket second = CoverageBucket.create("15.7", Tier.DIAMOND);
    given(coverageBucketRepository.findAllByOrderByIdAsc()).willReturn(List.of(first, second));

    List<CoverageBucketResponse> responses = service.getAll();

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).tier()).isEqualTo(Tier.MASTER);
    assertThat(responses.get(1).tier()).isEqualTo(Tier.DIAMOND);
  }
}
