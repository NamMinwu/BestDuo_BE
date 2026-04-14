package com.bestduo_BE.seed.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SeedBootstrapControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SeedBootstrapExecutor seedBootstrapRun;

  @Test
  @DisplayName("POST /seed/bootstrap — 명시적 파라미터로 유스케이스를 호출하고 결과를 반환한다")
  void run_withExplicitParametersInvokesUsecaseAndReturnsResult() throws Exception {
    SeedBootstrapExecutor.SeedBootstrapResult result =
        new SeedBootstrapExecutor.SeedBootstrapResult(1, 2, 3, 4, 5);
    given(seedBootstrapRun.execute(any())).willReturn(result);

    mockMvc.perform(post("/seed/bootstrap")
            .param("queue", "RANKED_SOLO_5x5")
            .param("tier", "MASTER")
            .param("division", "I")
            .param("seedTier", "CHALLENGER")
            .param("startPage", "2")
            .param("endPage", "4")
            .param("matchesPerPuuid", "15")
            .param("maxEntries", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagesProcessed").value(1))
        .andExpect(jsonPath("$.entriesFetched").value(2))
        .andExpect(jsonPath("$.matchIdsEnqueued").value(5));

    ArgumentCaptor<SeedBootstrapCommand> captor =
        ArgumentCaptor.forClass(SeedBootstrapCommand.class);
    then(seedBootstrapRun).should().execute(captor.capture());
    SeedBootstrapCommand command = captor.getValue();
    assertThat(command.queue()).isEqualTo("RANKED_SOLO_5x5");
    assertThat(command.tier()).isEqualTo("MASTER");
    assertThat(command.division()).isEqualTo("I");
    assertThat(command.seedTier()).isEqualTo(Tier.CHALLENGER);
    assertThat(command.startPage()).isEqualTo(2);
    assertThat(command.endPage()).isEqualTo(4);
    assertThat(command.matchesPerPuuid()).isEqualTo(15);
    assertThat(command.maxEntries()).isEqualTo(50);
  }

  @Test
  @DisplayName("POST /seed/bootstrap — 선택 파라미터 생략 시 기본값을 사용한다")
  void run_withoutOptionalParametersUsesDefaults() throws Exception {
    SeedBootstrapExecutor.SeedBootstrapResult result =
        new SeedBootstrapExecutor.SeedBootstrapResult(0, 0, 0, 0, 0);
    given(seedBootstrapRun.execute(any())).willReturn(result);

    mockMvc.perform(post("/seed/bootstrap")
            .param("queue", "RANKED_FLEX_SR")
            .param("tier", "DIAMOND")
            .param("division", "II"))
        .andExpect(status().isOk());

    ArgumentCaptor<SeedBootstrapCommand> captor =
        ArgumentCaptor.forClass(SeedBootstrapCommand.class);
    then(seedBootstrapRun).should().execute(captor.capture());
    SeedBootstrapCommand command = captor.getValue();
    assertThat(command.seedTier()).isEqualTo(Tier.EMERALD);
    assertThat(command.startPage()).isEqualTo(1);
    assertThat(command.endPage()).isEqualTo(3);
    assertThat(command.matchesPerPuuid()).isEqualTo(10);
    assertThat(command.maxEntries()).isZero();
  }
}
