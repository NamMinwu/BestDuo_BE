package com.bestduo_BE.common.presentation.api;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.presentation.api.dto.PatchVersionResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/patch")
public class AdminPatchController {

  private final PatchVersionService patchVersionService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PatchVersionResponse register(
      @RequestParam String patch,
      @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime releasedAt
  ) {
    boolean registered = patchVersionService.registerIfAbsent(patch, releasedAt);
    if (!registered) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Patch already registered: " + patch);
    }
    return new PatchVersionResponse(patch, releasedAt);
  }

  @GetMapping("/current")
  public PatchVersionResponse current() {
    return patchVersionService.currentPatch()
        .map(p -> new PatchVersionResponse(p.getPatch(), p.getReleasedAt()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No patch version registered"));
  }
}
