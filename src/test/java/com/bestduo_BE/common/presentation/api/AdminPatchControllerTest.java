package com.bestduo_BE.common.presentation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminPatchController.class)
class AdminPatchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PatchVersionService patchVersionService;

  @Test
  @DisplayName("POST /admin/patch — 신규 패치 등록 시 201 Created 반환")
  void register_newPatch_returns201WithPatchInfo() throws Exception {
    given(patchVersionService.registerIfAbsent(eq("15.23"), any())).willReturn(true);

    mockMvc.perform(post("/admin/patch")
            .header("X-Admin-Key", "test-admin-key")
            .param("patch", "15.23")
            .param("releasedAt", "2026-04-02T00:00:00Z"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.patch").value("15.23"));

    then(patchVersionService).should().registerIfAbsent(eq("15.23"), any());
  }

  @Test
  @DisplayName("POST /admin/patch — 이미 존재하는 패치면 409 Conflict 반환")
  void register_existingPatch_returns409() throws Exception {
    given(patchVersionService.registerIfAbsent(eq("15.23"), any())).willReturn(false);

    mockMvc.perform(post("/admin/patch")
            .header("X-Admin-Key", "test-admin-key")
            .param("patch", "15.23")
            .param("releasedAt", "2026-04-02T00:00:00Z"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("GET /admin/patch/current — 패치 있으면 200 OK와 현재 패치 반환")
  void current_whenPatchExists_returns200WithCurrentPatch() throws Exception {
    OffsetDateTime releasedAt = OffsetDateTime.parse("2026-04-02T00:00:00+00:00");
    PatchVersion patchVersion = PatchVersion.of("15.23", releasedAt);
    given(patchVersionService.currentPatch()).willReturn(Optional.of(patchVersion));

    mockMvc.perform(get("/admin/patch/current")
            .header("X-Admin-Key", "test-admin-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patch").value("15.23"));
  }

  @Test
  @DisplayName("GET /admin/patch/current — 패치 없으면 404 Not Found 반환")
  void current_whenNoPatch_returns404() throws Exception {
    given(patchVersionService.currentPatch()).willReturn(Optional.empty());

    mockMvc.perform(get("/admin/patch/current")
            .header("X-Admin-Key", "test-admin-key"))
        .andExpect(status().isNotFound());
  }
}
