package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoStats;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  void aggregateReturnsUseCaseResult() throws Exception {
    AggregateBottomDuoStats.Result result = new AggregateBottomDuoStats.Result(10, 7);
    when(aggregateBottomDuoStat.execute()).thenReturn(result);

    mockMvc.perform(post("/admin/aggregate/bottom-duo-stat"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(result)));

    verify(aggregateBottomDuoStat).execute();
  }
}
