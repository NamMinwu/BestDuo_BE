package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.common.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@RequiredArgsConstructor
public class RiotRateLimitInterceptor implements ClientHttpRequestInterceptor {

  private final RiotKeyPool keyPool;

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution
  ) throws IOException {

    RiotRequestBudget.consume(1);
    try (KeyLease lease = keyPool.lease()) {
      request.getHeaders().set("X-Riot-Token", lease.apiKey());
      lease.acquire();

      ClientHttpResponse response = execution.execute(request, body);

      if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        String retryAfter = response.getHeaders().getFirst("Retry-After");
        lease.markRateLimited(parseRetryAfter(retryAfter));
        String msg = "Riot API rate limited (429). retry-after=" + retryAfter
            + ", uri=" + request.getURI();

        throw new RiotRateLimitedException(msg);
      }

      return response;
    }
  }

  private Duration parseRetryAfter(String retryAfter) {
    try {
      return retryAfter == null || retryAfter.isBlank()
          ? keyPool.defaultRetryAfter()
          : Duration.ofSeconds(Long.parseLong(retryAfter));
    } catch (NumberFormatException e) {
      return keyPool.defaultRetryAfter();
    }
  }
}
