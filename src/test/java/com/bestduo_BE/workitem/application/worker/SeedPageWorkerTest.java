package com.bestduo_BE.workitem.application.worker;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeedPageWorkerTest {

  @Mock private SeedBootstrapExecutor seedBootstrapExecutor;
  @Mock private KeyLease keyLease;

  private SeedPageWorker worker;

  @BeforeEach
  void setUp() {
    worker = new SeedPageWorker(seedBootstrapExecutor, new ObjectMapper());
  }

  @Test
  void executeUsesPayloadPageQueueAndDivision() {
    WorkItem item = WorkItem.pending(
        1L,
        "15.7",
        Tier.MASTER,
        WorkItemType.SEED_SUMMONERS,
        1,
        2,
        "{\"queue\":\"RANKED_SOLO_5x5\",\"division\":\"II\",\"page\":3,\"tier\":\"MASTER\"}"
    );

    worker.execute(item, keyLease);

    ArgumentCaptor<SeedBootstrapCommand> captor = ArgumentCaptor.forClass(SeedBootstrapCommand.class);
    verify(seedBootstrapExecutor).execute(captor.capture());
    SeedBootstrapCommand command = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(command.startPage()).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(command.endPage()).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(command.division()).isEqualTo("II");
    org.assertj.core.api.Assertions.assertThat(command.maxEntries()).isEqualTo(2);
  }
}
