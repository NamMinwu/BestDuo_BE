package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoMatchup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BottomDuoMatchupAggregateController.class)
class BottomDuoMatchupAggregateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AggregateBottomDuoMatchup aggregateBottomDuoMatchup;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("POST /admin/aggregate/bottom-duo-matchup — 유스케이스 결과를 반환한다")
  void runReturnsUseCaseResult() throws Exception {
    AggregateBottomDuoMatchup.Result result = new AggregateBottomDuoMatchup.Result(12);
    when(aggregateBottomDuoMatchup.execute()).thenReturn(result);

    mockMvc.perform(post("/admin/aggregate/bottom-duo-matchup")
            .header("X-Admin-Key", "test-admin-key"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(result)));

    verify(aggregateBottomDuoMatchup).execute();
  }
}
