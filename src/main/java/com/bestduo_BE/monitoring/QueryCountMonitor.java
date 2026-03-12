package com.bestduo_BE.monitoring;

import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.springframework.stereotype.Component;

@Component
public class QueryCountMonitor {

  public void reset() {
    QueryCountHolder.clear();
  }

  public QueryStats snapshotAndReset() {
    QueryCount queryCount = QueryCountHolder.getGrandTotal();
    QueryCountHolder.clear();
    return QueryStats.from(queryCount);
  }

  public record QueryStats(long total, long select, long insert, long update, long delete, long other) {
    private static final QueryStats EMPTY = new QueryStats(0, 0, 0, 0, 0, 0);

    public static QueryStats empty() {
      return EMPTY;
    }

    static QueryStats from(QueryCount queryCount) {
      if (queryCount == null) {
        return EMPTY;
      }
      return new QueryStats(
          queryCount.getTotal(),
          queryCount.getSelect(),
          queryCount.getInsert(),
          queryCount.getUpdate(),
          queryCount.getDelete(),
          queryCount.getOther()
      );
    }
  }
}
