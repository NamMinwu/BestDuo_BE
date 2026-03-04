package com.bestduo_BE.infra.riot;

import com.bestduo_BE.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.infra.riot.exception.RiotRateLimitedException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@RequiredArgsConstructor
public class RiotRateLimitInterceptor implements ClientHttpRequestInterceptor {

  private final DualWindowRateLimiter rateLimiter;

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution
  ) throws IOException {

    RiotRequestBudget.consume(1);
    // ✅ 모든 Riot API 요청에 공통 적용
    rateLimiter.acquire();

    ClientHttpResponse response = execution.execute(request, body);

    // ✅ 429 감지 (개발키 보호 모드에서 유용)
    if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      String retryAfter = response.getHeaders().getFirst("Retry-After");
      String msg = "Riot API rate limited (429). retry-after=" + retryAfter
          + ", uri=" + request.getURI();

      // response body를 읽고 싶다면 여기서 읽어야 하지만, 보통 헤더/URI만으로 충분
      throw new RiotRateLimitedException(msg);
    }

    return response;
  }
}
