package com.bestduo_BE.workitem.application.worker;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;

public interface WorkerContract {

  WorkItemType type();

  void execute(WorkItem workItem, KeyLease keyLease);
}
