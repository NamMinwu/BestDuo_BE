package com.bestduo_BE.infra.riot.budget;

public class BudgetExhaustedException extends RuntimeException {

  public BudgetExhaustedException(String message) {
    super(message);
  }
}
