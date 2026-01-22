package com.bestduo_BE.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.application.SeedExpansionWorker;
import com.bestduo_BE.domain.model.Tier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SeedExpansionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SeedExpansionWorker worker;

  @Test
  void expand_withExplicitParametersInvokesWorkerAndReturnsResult() throws Exception {
    var result = new SeedExpansionWorker.ExpansionResult(3, 9, 27, 81);
    given(worker.execute(anyInt(), anyInt(), any())).willReturn(result);

    mockMvc.perform(post("/seed/expand")
            .param("batchSize", "10")
            .param("matchesPerPuuid", "7")
            .param("collectionTier", "CHALLENGER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puuidPicked").value(3))
        .andExpect(jsonPath("$.matchIdsFetched").value(9))
        .andExpect(jsonPath("$.rawCreated").value(27))
        .andExpect(jsonPath("$.seedsExpanded").value(81));

    ArgumentCaptor<Integer> batchCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> matchesCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Tier> tierCaptor = ArgumentCaptor.forClass(Tier.class);
    then(worker).should().execute(batchCaptor.capture(), matchesCaptor.capture(), tierCaptor.capture());
    assertThat(batchCaptor.getValue()).isEqualTo(10);
    assertThat(matchesCaptor.getValue()).isEqualTo(7);
    assertThat(tierCaptor.getValue()).isEqualTo(Tier.CHALLENGER);
  }

  @Test
  void expand_withoutParametersUsesDefaults() throws Exception {
    given(worker.execute(anyInt(), anyInt(), any())).willReturn(
        new SeedExpansionWorker.ExpansionResult(0, 0, 0, 0));

    mockMvc.perform(post("/seed/expand"))
        .andExpect(status().isOk());

    then(worker).should().execute(50, 5, Tier.EMERALD_PLUS);
  }
}
