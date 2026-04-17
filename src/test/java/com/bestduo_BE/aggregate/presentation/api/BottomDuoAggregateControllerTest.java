package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoStats;
import com.bestduo_BE.common.domain.model.Tier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BottomDuoAggregateController.class)
class BottomDuoAggregateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean
  private AggregateBottomDuoStats aggregateBottomDuoStat;

  @Test
  @DisplayName("POST /admin/aggregate/bottom-duo-stat — patchVersion만 주어지면 tier=null로 호출한다")
  void aggregateReturnsUseCaseResultWithPatchScope() throws Exception {
    AggregateBottomDuoStats.Result result = new AggregateBottomDuoStats.Result(10, 7);
    when(aggregateBottomDuoStat.execute("14.10", null)).thenReturn(result);

    mockMvc.perform(post("/admin/aggregate/bottom-duo-stat")
            .header("X-Admin-Key", "test-admin-key")
            .param("patchVersion", "14.10"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(result)));

    verify(aggregateBottomDuoStat).execute("14.10", null);
  }

  @Test
  @DisplayName("POST /admin/aggregate/bottom-duo-stat — tier가 주어지면 유스케이스에 그대로 전달한다")
  void aggregatePassesTierWhenProvided() throws Exception {
    AggregateBottomDuoStats.Result result = new AggregateBottomDuoStats.Result(5, 3);
    when(aggregateBottomDuoStat.execute("14.10", Tier.EMERALD)).thenReturn(result);

    mockMvc.perform(post("/admin/aggregate/bottom-duo-stat")
            .header("X-Admin-Key", "test-admin-key")
            .param("patchVersion", "14.10")
            .param("tier", "EMERALD"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(result)));

    verify(aggregateBottomDuoStat).execute("14.10", Tier.EMERALD);
  }

  @Test
  @DisplayName("POST /admin/aggregate/bottom-duo-stat — patchVersion이 없으면 400을 반환한다")
  void aggregateRequiresPatchVersion() throws Exception {
    mockMvc.perform(post("/admin/aggregate/bottom-duo-stat")
            .header("X-Admin-Key", "test-admin-key"))
        .andExpect(status().isBadRequest());
  }
}
