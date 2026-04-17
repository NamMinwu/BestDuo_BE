package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.GetBottomDuoStatistics;
import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoStatisticsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BottomDuoStatisticsController.class)
class BottomDuoStatisticsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean
  private GetBottomDuoStatistics viewBottomDuoStatistics;

  @Test
  @DisplayName("GET /bottom-duo/stats — 유스케이스 응답을 반환한다")
  void getListReturnsUseCaseResponse() throws Exception {
    BottomDuoStatisticsResponse response = new BottomDuoStatisticsResponse(
        "GOLD",
        "14.10",
        500,
        List.of(new BottomDuoStatisticsResponse.Item(
            "ashe",
            "Ashe",
            "adc.png",
            "thresh",
            "Thresh",
            "sup.png",
            0.55,
            0.12,
            60,
            2,
            5,
            -1
        ))
    );

    when(viewBottomDuoStatistics.execute(
        Tier.GOLD,
        "14.10",
        "Ashe",
        "Thresh",
        BottomDuoStatFinder.SortKey.WINRATE_ASC
    )).thenReturn(response);

    mockMvc.perform(get("/bottom-duo/stats")
            .param("tier", "GOLD")
            .param("patchVersion", "14.10")
            .param("adcChampionId", "Ashe")
            .param("supChampionId", "Thresh")
            .param("sort", "WINRATE_ASC"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(response)));

    verify(viewBottomDuoStatistics).execute(
        Tier.GOLD,
        "14.10",
        "Ashe",
        "Thresh",
        BottomDuoStatFinder.SortKey.WINRATE_ASC
    );
  }

  @Test
  @DisplayName("GET /bottom-duo/stats — sort 파라미터 미제공 시 기본 정렬을 사용한다")
  void getListUsesDefaultSortWhenNotProvided() throws Exception {
    BottomDuoStatisticsResponse response = new BottomDuoStatisticsResponse("SILVER", null, 0, List.of());
    when(viewBottomDuoStatistics.execute(
        Tier.SILVER,
        null,
        null,
        null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC
    )).thenReturn(response);

    mockMvc.perform(get("/bottom-duo/stats")
            .param("tier", "SILVER"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(response)));

    verify(viewBottomDuoStatistics).execute(
        Tier.SILVER,
        null,
        null,
        null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC
    );
  }
}
