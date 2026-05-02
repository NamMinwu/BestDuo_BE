package com.bestduo_BE.common.presentation.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.infra.persistence.entity.PatchVersion;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PatchController.class)
class PatchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PatchVersionService patchVersionService;

  @Test
  @DisplayName("GET /patch/recent — 최신 2개 패치를 최신 → 이전 순으로 200 OK 반환")
  void recent_whenTwoExist_returns200WithBothOrdered() throws Exception {
    PatchVersion latest = PatchVersion.of("16.8", OffsetDateTime.parse("2026-04-20T00:00:00+00:00"));
    PatchVersion previous = PatchVersion.of("16.7", OffsetDateTime.parse("2026-04-01T00:00:00+00:00"));
    given(patchVersionService.recentPatches()).willReturn(List.of(latest, previous));

    mockMvc.perform(get("/patch/recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].patch").value("16.8"))
        .andExpect(jsonPath("$[1].patch").value("16.7"));
  }

  @Test
  @DisplayName("GET /patch/recent — 패치가 1개뿐이면 1개만 반환")
  void recent_whenOnlyOneExists_returnsSingleton() throws Exception {
    PatchVersion only = PatchVersion.of("16.8", OffsetDateTime.parse("2026-04-20T00:00:00+00:00"));
    given(patchVersionService.recentPatches()).willReturn(List.of(only));

    mockMvc.perform(get("/patch/recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].patch").value("16.8"));
  }

  @Test
  @DisplayName("GET /patch/recent — 패치가 없으면 빈 배열 반환")
  void recent_whenEmpty_returnsEmptyArray() throws Exception {
    given(patchVersionService.recentPatches()).willReturn(List.of());

    mockMvc.perform(get("/patch/recent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
