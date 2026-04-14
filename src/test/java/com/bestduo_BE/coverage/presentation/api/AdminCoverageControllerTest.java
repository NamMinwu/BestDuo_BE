package com.bestduo_BE.coverage.presentation.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.application.CoverageBucketService;
import com.bestduo_BE.coverage.application.exception.CoverageBucketNotFoundException;
import com.bestduo_BE.coverage.presentation.api.dto.CoverageBucketResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCoverageController.class)
class AdminCoverageControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CoverageBucketService coverageBucketService;

  @Test
  @DisplayName("전체 버킷 목록 조회 성공")
  void getAllReturnsBuckets() throws Exception {
    given(coverageBucketService.getAll()).willReturn(List.of(
        new CoverageBucketResponse(1L, "15.7", Tier.MASTER, 20_000L, 3_400L, 16_600L, "COLLECTING", 1,
            OffsetDateTime.parse("2026-03-30T10:15:30+09:00"))
    ));

    mockMvc.perform(get("/admin/coverage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].tier").value("MASTER"))
        .andExpect(jsonPath("$[0].currentMatchCount").value(3400));
  }

  @Test
  @DisplayName("단건 버킷 조회 성공")
  void getReturnsBucket() throws Exception {
    given(coverageBucketService.get(1L)).willReturn(
        new CoverageBucketResponse(1L, "15.7", Tier.MASTER, 20_000L, 3_400L, 16_600L, "COLLECTING", 1,
            OffsetDateTime.parse("2026-03-30T10:15:30+09:00"))
    );

    mockMvc.perform(get("/admin/coverage/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.tier").value("MASTER"))
        .andExpect(jsonPath("$.deficitMatchCount").value(16600));
  }

  @Test
  @DisplayName("존재하지 않는 버킷 조회 시 404 반환")
  void getReturnsNotFoundWhenBucketIsMissing() throws Exception {
    given(coverageBucketService.get(99L)).willThrow(new CoverageBucketNotFoundException(99L));

    mockMvc.perform(get("/admin/coverage/99"))
        .andExpect(status().isNotFound());
  }
}
