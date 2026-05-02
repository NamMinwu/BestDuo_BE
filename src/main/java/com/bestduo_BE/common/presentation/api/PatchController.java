package com.bestduo_BE.common.presentation.api;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.presentation.api.dto.PatchVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "패치 정보", description = "패치 버전 관련 공개 조회 API입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/patch")
public class PatchController {

  private final PatchVersionService patchVersionService;

  @Operation(
      summary = "최근 패치 2개 조회",
      description = "최신 → 이전 순으로 최대 2개의 패치를 반환합니다. 1개만 등록되어 있으면 1개만, 없으면 빈 배열을 반환합니다."
  )
  @GetMapping("/recent")
  public List<PatchVersionResponse> recent() {
    return patchVersionService.recentPatches().stream()
        .map(p -> new PatchVersionResponse(p.getPatch(), p.getReleasedAt()))
        .toList();
  }
}
