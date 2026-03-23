package com.bestduo_BE.common.infra.riot.budget;

public class BudgetExhaustedException extends RuntimeException {

  public BudgetExhaustedException(String message) {
    super(message);
  }
}
