package com.bestduo_BE.workitem.application.worker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeedPageWorker implements WorkerContract {

  private final SeedBootstrapExecutor seedBootstrapExecutor;
  private final ObjectMapper objectMapper;

  @Override
  public WorkItemType type() {
    return WorkItemType.SEED_SUMMONERS;
  }

  @Override
  public void execute(WorkItem workItem) {
    SeedPayload payload = parsePayload(workItem.getPayload());
    seedBootstrapExecutor.execute(new SeedBootstrapCommand(
        payload.queue(),
        payload.tier(),
        payload.division(),
        workItem.getTier(),
        payload.page(),
        payload.page(),
        20,
        workItem.getBatchLimit() == null ? 0 : workItem.getBatchLimit()
    ));
  }

  private SeedPayload parsePayload(String rawPayload) {
    if (rawPayload == null || rawPayload.isBlank()) {
      return new SeedPayload("RANKED_SOLO_5x5", "I", 1, null);
    }
    try {
      JsonNode node = objectMapper.readTree(rawPayload);
      return new SeedPayload(
          text(node, "queue", "RANKED_SOLO_5x5"),
          text(node, "division", "I"),
          node.path("page").asInt(1),
          text(node, "tier", null)
      );
    } catch (Exception e) {
      return new SeedPayload("RANKED_SOLO_5x5", "I", 1, null);
    }
  }

  private String text(JsonNode node, String field, String fallback) {
    return node.hasNonNull(field) ? node.get(field).asText() : fallback;
  }

  private record SeedPayload(String queue, String division, int page, String tier) {
  }
}
