package com.bestduo_BE.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.application.ViewBottomDuoStatistics;
import com.bestduo_BE.application.port.BottomDuoStatFinder;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
  private ViewBottomDuoStatistics viewBottomDuoStatistics;

  @Test
  void getListReturnsUseCaseResponse() throws Exception {
    BottomDuoStatisticsResponse response = new BottomDuoStatisticsResponse(
        "GOLD",
        500,
        List.of(new BottomDuoStatisticsResponse.Item(
            "Ashe",
            "adc.png",
            "Thresh",
            "sup.png",
            0.55,
            0.12,
            60
        ))
    );

    when(viewBottomDuoStatistics.execute(
        Tier.GOLD,
        "Ashe",
        "Thresh",
        BottomDuoStatFinder.SortKey.WINRATE_ASC
    )).thenReturn(response);

    mockMvc.perform(get("/bottom-duo/stats")
            .param("tier", "GOLD")
            .param("adcChampionId", "Ashe")
            .param("supChampionId", "Thresh")
            .param("sort", "WINRATE_ASC"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(response)));

    verify(viewBottomDuoStatistics).execute(
        Tier.GOLD,
        "Ashe",
        "Thresh",
        BottomDuoStatFinder.SortKey.WINRATE_ASC
    );
  }

  @Test
  void getListUsesDefaultSortWhenNotProvided() throws Exception {
    BottomDuoStatisticsResponse response = new BottomDuoStatisticsResponse("SILVER", 0, List.of());
    when(viewBottomDuoStatistics.execute(
        Tier.SILVER,
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
        BottomDuoStatFinder.SortKey.PICKRATE_DESC
    );
  }
}
