package com.bestduo_BE.presentation.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.application.RefreshBatchRun;
import com.bestduo_BE.application.RefreshSummonerMatches;
import com.bestduo_BE.domain.model.Tier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RefreshControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RefreshSummonerMatches refreshSummonerMatches;

  @MockitoBean
  private RefreshBatchRun refreshBatchRun;

  @Test
  void refreshOneEndpointDelegatesToUsecaseAndReturnsResult() throws Exception {
    RefreshSummonerMatches.Result usecaseResult =
        new RefreshSummonerMatches.Result("puuid-1", 7, Tier.EMERALD_PLUS, 1700000000L);
    given(refreshSummonerMatches.execute("puuid-1")).willReturn(usecaseResult);

    mockMvc.perform(post("/admin/refresh/one")
            .param("puuid", "puuid-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puuid").value("puuid-1"))
        .andExpect(jsonPath("$.rawCreated").value(7))
        .andExpect(jsonPath("$.collectionTier").value("EMERALD_PLUS"))
        .andExpect(jsonPath("$.lastMatchStartTimeSec").value(1700000000L));

    then(refreshSummonerMatches).should().execute("puuid-1");
  }

  @Test
  void refreshBatchEndpointPassesLimitParameterAndReturnsResult() throws Exception {
    RefreshBatchRun.Result batchResult = new RefreshBatchRun.Result(12, 45);
    given(refreshBatchRun.execute(25)).willReturn(batchResult);

    mockMvc.perform(post("/admin/refresh/batch")
            .param("limit", "25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(12))
        .andExpect(jsonPath("$.rawCreated").value(45));

    then(refreshBatchRun).should().execute(25);
  }

  @Test
  void refreshBatchEndpointUsesDefaultLimitWhenMissing() throws Exception {
    RefreshBatchRun.Result batchResult = new RefreshBatchRun.Result(0, 0);
    given(refreshBatchRun.execute(50)).willReturn(batchResult);

    mockMvc.perform(post("/admin/refresh/batch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(0))
        .andExpect(jsonPath("$.rawCreated").value(0));

    then(refreshBatchRun).should().execute(50);
  }
}

