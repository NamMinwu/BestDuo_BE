package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.ComputeBottomDuoRanking;
import com.bestduo_BE.common.domain.model.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AggregateAdminController.class)
class AggregateAdminControllerTest {

  private static final String PATCH = "16.10";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ComputeBottomDuoRanking rankingUseCase;

  @Test
  @DisplayName("POST /admin/aggregate/recompute-ranking — tiers 미지정 시 CHALLENGER~EMERALD 5개 tier 전부 실행")
  void recomputeRanking_defaultTiers_invokesAllFiveTiers() throws Exception {
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.CHALLENGER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 10));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.GRANDMASTER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 20));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.MASTER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 2053));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.DIAMOND.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 1781));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.EMERALD.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 0));

    mockMvc.perform(post("/admin/aggregate/recompute-ranking")
            .header("X-Admin-Key", "test-admin-key")
            .param("patch", PATCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patch").value(PATCH))
        .andExpect(jsonPath("$.totalUpdated").value(10 + 20 + 2053 + 1781 + 0))
        .andExpect(jsonPath("$.results.length()").value(5))
        .andExpect(jsonPath("$.results[2].tier").value("MASTER"))
        .andExpect(jsonPath("$.results[2].updatedRows").value(2053));

    then(rankingUseCase).should().execute(PATCH, Tier.CHALLENGER.name());
    then(rankingUseCase).should().execute(PATCH, Tier.GRANDMASTER.name());
    then(rankingUseCase).should().execute(PATCH, Tier.MASTER.name());
    then(rankingUseCase).should().execute(PATCH, Tier.DIAMOND.name());
    then(rankingUseCase).should().execute(PATCH, Tier.EMERALD.name());
  }

  @Test
  @DisplayName("POST /admin/aggregate/recompute-ranking — tiers 지정 시 해당 tier 만 실행")
  void recomputeRanking_specifiedTiers_invokesOnlyThose() throws Exception {
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.MASTER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 2053));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.EMERALD.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 0));

    mockMvc.perform(post("/admin/aggregate/recompute-ranking")
            .header("X-Admin-Key", "test-admin-key")
            .param("patch", PATCH)
            .param("tiers", "MASTER", "EMERALD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(2))
        .andExpect(jsonPath("$.totalUpdated").value(2053));

    then(rankingUseCase).should().execute(PATCH, Tier.MASTER.name());
    then(rankingUseCase).should().execute(PATCH, Tier.EMERALD.name());
    then(rankingUseCase).should(never()).execute(PATCH, Tier.CHALLENGER.name());
    then(rankingUseCase).should(never()).execute(PATCH, Tier.GRANDMASTER.name());
    then(rankingUseCase).should(never()).execute(PATCH, Tier.DIAMOND.name());
  }

  @Test
  @DisplayName("POST /admin/aggregate/recompute-ranking — 특정 tier 가 예외를 던져도 나머지 tier 는 계속 진행한다")
  void recomputeRanking_oneTierFails_othersContinue() throws Exception {
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.CHALLENGER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 5));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.GRANDMASTER.name())))
        .willThrow(new RuntimeException("boom"));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.MASTER.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 100));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.DIAMOND.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 80));
    given(rankingUseCase.execute(eq(PATCH), eq(Tier.EMERALD.name())))
        .willReturn(new ComputeBottomDuoRanking.Result(PATCH, 0));

    mockMvc.perform(post("/admin/aggregate/recompute-ranking")
            .header("X-Admin-Key", "test-admin-key")
            .param("patch", PATCH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(5))
        .andExpect(jsonPath("$.results[1].tier").value("GRANDMASTER"))
        .andExpect(jsonPath("$.results[1].error").value("boom"))
        .andExpect(jsonPath("$.totalUpdated").value(5 + 100 + 80 + 0));
  }
}
