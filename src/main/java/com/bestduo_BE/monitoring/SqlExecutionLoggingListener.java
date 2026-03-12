package com.bestduo_BE.monitoring;

import java.util.List;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqlExecutionLoggingListener implements QueryExecutionListener {

  private static final Logger SQL_LOGGER = LoggerFactory.getLogger("com.bestduo_BE.SQL");

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // no-op
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    if (!SQL_LOGGER.isDebugEnabled()) {
      return;
    }
    long elapsedMs = execInfo.getElapsedTime();
    boolean success = execInfo.getThrowable() == null;
    boolean batch = execInfo.isBatch();

    for (QueryInfo queryInfo : queryInfoList) {
      SQL_LOGGER.debug(
          "SQL timeMs={} success={} batch={} query={} params={}",
          elapsedMs,
          success,
          batch,
          sanitize(queryInfo.getQuery()),
          formatParameters(queryInfo)
      );
    }
  }

  private String sanitize(String query) {
    if (query == null) {
      return null;
    }
    return query.replaceAll("\s+", " ").trim();
  }

  private String formatParameters(QueryInfo queryInfo) {
    List<?> parameterSets = queryInfo.getParametersList();
    if (parameterSets == null || parameterSets.isEmpty()) {
      return "[]";
    }
    return parameterSets.toString();
  }
}
