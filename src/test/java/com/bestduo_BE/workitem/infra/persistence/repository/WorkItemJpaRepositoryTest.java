package com.bestduo_BE.workitem.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:work-item-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false"
})
class WorkItemJpaRepositoryTest {

  @Autowired private WorkItemJpaRepository repository;

  @Test
  void existsByCoverageBucketIdAndTypeAndStatusInDetectsPendingWork() {
    repository.save(WorkItem.ready(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10));

    boolean exists = repository.existsByCoverageBucketIdAndTypeAndStatusIn(
        1L,
        WorkItemType.VERIFY_SUMMONERS,
        List.of(WorkItemStatus.READY, WorkItemStatus.RUNNING)
    );

    assertThat(exists).isTrue();
  }

  @Test
  void countByPatchAndTierAndStatusCountsMatchingRows() {
    repository.save(WorkItem.ready(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10));
    repository.save(WorkItem.ready(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10));

    long count = repository.countByPatchAndTierAndStatus("15.7", Tier.MASTER, WorkItemStatus.READY);

    assertThat(count).isEqualTo(2L);
  }

}
