package com.bestduo_BE.coverage.presentation.api;

import com.bestduo_BE.coverage.application.CoverageBucketService;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/coverage")
public class AdminCoverageController {

  private final CoverageBucketService coverageBucketService;

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
