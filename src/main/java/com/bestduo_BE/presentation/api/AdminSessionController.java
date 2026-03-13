package com.bestduo_BE.presentation.api;

import com.bestduo_BE.application.SessionRunRequestService;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.SessionRunCreateRequest;
import com.bestduo_BE.presentation.api.dto.SessionRunRequestResponse;
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
@RequestMapping("/admin/session")
public class AdminSessionController {

  private final SessionRunRequestService sessionRunRequestService;

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public SessionRunRequestResponse run(
      @RequestParam(defaultValue = "200") int budgetTotal,
      @RequestParam(required = false) Double seedRatio,
      @RequestParam(required = false) Double refreshRatio,
      @RequestParam(defaultValue = "10") int consumeLimitPerCycle,
      @RequestParam(defaultValue = "20") int maxConsumeCycles,
      @RequestParam(required = false) Integer refreshLimit,
      @RequestParam(required = false) Tier tier
  ) {
    try {
      return sessionRunRequestService.create(
          new SessionRunCreateRequest(
              budgetTotal,
              seedRatio,
              refreshRatio,
              consumeLimitPerCycle,
              maxConsumeCycles,
              refreshLimit,
              tier
          )
      );
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @GetMapping("/requests")
  public List<SessionRunRequestResponse> getAll() {
    return sessionRunRequestService.getAll();
  }

  @GetMapping("/requests/{id}")
  public SessionRunRequestResponse get(@PathVariable Long id) {
    return sessionRunRequestService.get(id);
  }

  @GetMapping("/requests/running")
  public SessionRunRequestResponse getRunning() {
    return sessionRunRequestService.getRunning();
  }

  @GetMapping("/requests/latest")
  public SessionRunRequestResponse getLatest() {
    return sessionRunRequestService.getLatest();
  }
}
