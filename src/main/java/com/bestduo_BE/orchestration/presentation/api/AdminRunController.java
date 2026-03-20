package com.bestduo_BE.orchestration.presentation.api;

import com.bestduo_BE.orchestration.application.RunRequestService;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.presentation.api.dto.RunCreateRequest;
import com.bestduo_BE.orchestration.presentation.api.dto.RunRequestResponse;
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
@RequestMapping("/admin/run")
public class AdminRunController {

  private final RunRequestService runRequestService;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public RunRequestResponse run(
      @RequestParam(defaultValue = "200") int budgetTotal,
      @RequestParam(required = false) Double seedRatio,
      @RequestParam(required = false) Double refreshRatio,
      @RequestParam(defaultValue = "10") int ingestLimitPerCycle,
      @RequestParam(defaultValue = "20") int maxIngestCycles,
      @RequestParam(required = false) Integer refreshLimit,
      @RequestParam(required = false) Tier tier
  ) {
    try {
      return runRequestService.create(
          new RunCreateRequest(
              budgetTotal,
              seedRatio,
              refreshRatio,
              ingestLimitPerCycle,
              maxIngestCycles,
              refreshLimit,
              tier
          )
      );
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @GetMapping("/requests")
  public List<RunRequestResponse> getAll() {
    return runRequestService.getAll();
  }

  @GetMapping("/requests/{id}")
  public RunRequestResponse get(@PathVariable Long id) {
    return runRequestService.get(id);
  }

  @GetMapping("/requests/running")
  public RunRequestResponse getRunning() {
    return runRequestService.getRunning();
  }

  @GetMapping("/requests/latest")
  public RunRequestResponse getLatest() {
    return runRequestService.getLatest();
  }
}
