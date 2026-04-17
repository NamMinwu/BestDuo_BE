package com.bestduo_BE.aggregate.presentation.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.aggregate.application.GetBottomDuoCounters;
import com.bestduo_BE.aggregate.application.GetBottomDuoDetailStatistics;
import com.bestduo_BE.aggregate.infra.persistence.BottomDuoMatchupFinder;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoCounterResponse;
import com.bestduo_BE.common.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BottomDuoDetailStatisticsController.class)
class BottomDuoDetailStatisticsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetBottomDuoDetailStatistics viewBottomDuoDetailStatistics;

  @MockitoBean
  private GetBottomDuoCounters viewBottomDuoCounters;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("GET /bottom-duo/matchups — 유스케이스 응답을 반환한다")
  void listReturnsUseCaseResponse() throws Exception {
    BottomDuoDetailStatisticsResponse response = new BottomDuoDetailStatisticsResponse(
        "GOLD",
        "14.10",
        200,
        new BottomDuoDetailStatisticsResponse.DuoMeta("ashe", "Ashe", "a.png", "lux", "Lux", "l.png"),
        List.of(new BottomDuoDetailStatisticsResponse.Item(
            "jinx",
            "Jinx",
            "j.png",
            "morgana",
            "Morgana",
            "m.png",
            0.45,
            0.2,
            40
        ))
    );

    when(viewBottomDuoDetailStatistics.execute(
        Tier.GOLD,
        "14.10",
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC
    )).thenReturn(response);

    mockMvc.perform(get("/bottom-duo/matchups")
            .param("tier", "GOLD")
            .param("patchVersion", "14.10")
            .param("adcChampionId", "ashe")
            .param("supChampionId", "lux")
            .param("oppAdcChampionId", "jinx")
            .param("oppSupChampionId", "morgana")
            .param("sort", "PICKRATE_ASC"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(response)));

    verify(viewBottomDuoDetailStatistics).execute(
        Tier.GOLD,
        "14.10",
        "ashe",
        "lux",
        "jinx",
        "morgana",
        BottomDuoMatchupFinder.SortKey.PICKRATE_ASC
    );
  }

  @Test
  @DisplayName("GET /bottom-duo/counters — 카운터 유스케이스 응답을 반환한다")
  void countersReturnsCounterUseCaseResponse() throws Exception {
    BottomDuoCounterResponse response = new BottomDuoCounterResponse(
        "DIAMOND",
        "14.9",
        500,
        new BottomDuoCounterResponse.DuoMeta("ashe", "Ashe", "a.png", "lux", "Lux", "l.png"),
        5,
        List.of()
    );

    when(viewBottomDuoCounters.execute(Tier.DIAMOND, "14.9", "ashe", "lux", 5)).thenReturn(response);

    mockMvc.perform(get("/bottom-duo/counters")
            .param("tier", "DIAMOND")
            .param("patchVersion", "14.9")
            .param("adcChampionId", "ashe")
            .param("supChampionId", "lux")
            .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(response)));

    verify(viewBottomDuoCounters).execute(Tier.DIAMOND, "14.9", "ashe", "lux", 5);
  }
}
