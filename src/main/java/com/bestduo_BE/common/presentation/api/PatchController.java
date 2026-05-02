package com.bestduo_BE.common.presentation.api;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.presentation.api.dto.PatchVersionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/patch")
public class PatchController {

  private final PatchVersionService patchVersionService;

  @GetMapping("/recent")
  public List<PatchVersionResponse> recent() {
    return patchVersionService.recentPatches().stream()
        .map(p -> new PatchVersionResponse(p.getPatch(), p.getReleasedAt()))
        .toList();
  }
}
