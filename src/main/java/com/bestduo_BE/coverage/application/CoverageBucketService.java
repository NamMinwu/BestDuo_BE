package com.bestduo_BE.coverage.application;

import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverageBucketService {

  private final CoverageBucketJpaRepository coverageBucketRepository;

  @Transactional(readOnly = true)
  public CoverageBucketResponse get(Long id) {
    CoverageBucket bucket = coverageBucketRepository.findById(id)
        .orElseThrow(() -> new CoverageBucketNotFoundException(id));
    return toResponse(bucket);
  }

  @Transactional(readOnly = true)
  public List<CoverageBucketResponse> getAll() {
    return coverageBucketRepository.findAllByOrderByIdAsc().stream()
        .map(this::toResponse)
        .toList();
  }

  private CoverageBucketResponse toResponse(CoverageBucket bucket) {
    return new CoverageBucketResponse(
        bucket.getId(),
        bucket.getPatch(),
        bucket.getTier()
    );
  }
}
