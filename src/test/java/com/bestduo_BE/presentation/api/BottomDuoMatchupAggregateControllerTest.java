package com.bestduo_BE.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.application.AggregateBottomDuoMatchup;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  void runReturnsUseCaseResult() throws Exception {
    AggregateBottomDuoMatchup.Result result = new AggregateBottomDuoMatchup.Result(12);
    when(aggregateBottomDuoMatchup.execute()).thenReturn(result);

    mockMvc.perform(post("/admin/aggregate/bottom-duo-matchup"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(result)));

    verify(aggregateBottomDuoMatchup).execute();
  }
}
