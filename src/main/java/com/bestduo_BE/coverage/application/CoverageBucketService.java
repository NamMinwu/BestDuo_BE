package com.bestduo_BE.coverage.application;

import com.bestduo_BE.coverage.application.exception.CoverageBucketAlreadyExistsException;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketCreateRequest;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverageBucketService {

  static final int DEFAULT_PRIORITY = 100;

  private final CoverageBucketJpaRepository coverageBucketRepository;
  private final CoverageBucketCountJpaRepository coverageBucketCountRepository;

  @Transactional
  public CoverageBucketResponse create(CoverageBucketCreateRequest request) {
    if (coverageBucketRepository.existsByPatchAndTier(request.patch(), request.tier())) {
      throw new CoverageBucketAlreadyExistsException(request.patch(), request.tier().name());
    }

    CoverageBucket bucket = CoverageBucket.create(
        request.patch(),
        request.tier(),
        request.targetMatchCount(),
        request.priority() != null ? request.priority() : DEFAULT_PRIORITY
    );
    bucket.refreshCount(currentCount(bucket));
    try {
      return toResponse(coverageBucketRepository.save(bucket));
    } catch (DataIntegrityViolationException e) {
      throw new CoverageBucketAlreadyExistsException(request.patch(), request.tier().name(), e);
    }
  }

  @Transactional
  public CoverageBucketResponse get(Long id) {
    CoverageBucket bucket = coverageBucketRepository.findById(id)
        .orElseThrow(() -> new CoverageBucketNotFoundException(id));
    bucket.refreshCount(currentCount(bucket));
    return toResponse(bucket);
  }

  @Transactional
  public List<CoverageBucketResponse> getAll() {
    return coverageBucketRepository.findAllByOrderByPriorityAscIdAsc().stream()
        .peek(bucket -> bucket.refreshCount(currentCount(bucket)))
        .map(this::toResponse)
        .toList();
  }

  private long currentCount(CoverageBucket bucket) {
    return coverageBucketCountRepository.countDistinctMatches(bucket.getPatch(), bucket.getTier().name());
  }

  private CoverageBucketResponse toResponse(CoverageBucket bucket) {
    long deficitMatchCount = Math.max(bucket.getTargetMatchCount() - bucket.getCurrentMatchCount(), 0L);
    return new CoverageBucketResponse(
        bucket.getId(),
        bucket.getPatch(),
        bucket.getTier(),
        bucket.getTargetMatchCount(),
        bucket.getCurrentMatchCount(),
        deficitMatchCount,
        bucket.getStatus().name(),
        bucket.getPriority(),
        bucket.getLastEvaluatedAt()
    );
  }
}
