package com.bestduo_BE.infra.riot.budget;

public class RiotRequestBudget {

  private RiotRequestBudget() {}

  private static final ThreadLocal<Integer> remaining = new ThreadLocal<>();

  public static void start(int budgetRequests) {
    remaining.set(budgetRequests);
  }

  public static void clear() {
    remaining.remove();
  }

  public static int remaining() {
    Integer v = remaining.get();
    return v == null ? Integer.MAX_VALUE : v; // 세션 밖에서는 무제한처럼
  }

  public static void consume(int cost) {
    Integer v = remaining.get();
    if (v == null) return; // 세션 밖이면 budget 적용 안 함

    int next = v - cost;
    if (next < 0) {
      remaining.set(0); // 예산이 바닥났음을 반영
      throw new BudgetExhaustedException("Riot request budget exhausted. remaining=" + v);
    }
    remaining.set(next);
  }
}
