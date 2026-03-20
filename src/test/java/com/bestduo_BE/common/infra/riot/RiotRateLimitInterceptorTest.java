package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.infra.riot.DualWindowRateLimiter;
import com.bestduo_BE.common.infra.riot.RiotRateLimitInterceptor;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RiotRateLimitInterceptorTest {

  private DualWindowRateLimiter rateLimiter;
  private RiotRateLimitInterceptor interceptor;
  private MockClientHttpRequest request;

  @BeforeEach
  void setUp() {
    rateLimiter = mock(DualWindowRateLimiter.class);
    interceptor = new RiotRateLimitInterceptor(rateLimiter);
    request = new MockClientHttpRequest();
    request.setURI(URI.create("https://riot.api/test"));
  }

  @Test
  void acquireCalledAndResponseReturnedWhenRequestSucceeds() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse ok = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    when(execution.execute(any(), any())).thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

    assertThat(result).isSameAs(ok);
    verify(rateLimiter).acquire();
  }

  @Test
  void throwsRiotRateLimitedExceptionOnTooManyRequests() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    MockClientHttpResponse tooMany = new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS);
    tooMany.getHeaders().set("Retry-After", "10");
    when(execution.execute(any(), any())).thenReturn(tooMany);

    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RiotRateLimitedException.class)
        .hasMessageContaining("retry-after=10")
        .hasMessageContaining("uri=" + request.getURI());

    verify(rateLimiter).acquire();
  }
}
