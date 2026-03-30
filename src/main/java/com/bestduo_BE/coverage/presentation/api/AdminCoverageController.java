package com.bestduo_BE.coverage.presentation.api;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.application.CoverageBucketService;
import com.bestduo_BE.coverage.application.exception.CoverageBucketAlreadyExistsException;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketCreateRequest;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/coverage")
public class AdminCoverageController {

  private final CoverageBucketService coverageBucketService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CoverageBucketResponse create(
      @RequestParam String patch,
      @RequestParam Tier tier,
      @RequestParam long targetMatchCount,
      @RequestParam(required = false) Integer priority
  ) {
    try {
      return coverageBucketService.create(new CoverageBucketCreateRequest(patch, tier, targetMatchCount, priority));
    } catch (CoverageBucketAlreadyExistsException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @GetMapping
  public List<CoverageBucketResponse> getAll() {
    return coverageBucketService.getAll();
  }

  @GetMapping("/{id}")
  public CoverageBucketResponse get(@PathVariable Long id) {
    try {
      return coverageBucketService.get(id);
    } catch (CoverageBucketNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }
}
