package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RiotRateLimitInterceptorTest {

  private RiotKeyPool keyPool;
  private KeyLease keyLease;
  private RiotRateLimitInterceptor interceptor;
  private MockClientHttpRequest request;

  @BeforeEach
  void setUp() {
    keyPool = mock(RiotKeyPool.class);
    keyLease = mock(KeyLease.class);
    interceptor = new RiotRateLimitInterceptor(keyPool);
    request = new MockClientHttpRequest();
    request.setURI(URI.create("https://riot.api/test"));
  }

  @Test
  void acquireCalledAndResponseReturnedWhenRequestSucceeds() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse ok = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    when(keyPool.leaseForRequest()).thenReturn(keyLease);
    when(keyLease.apiKey()).thenReturn("key-a");
    when(execution.execute(any(), any())).thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

    assertThat(result).isSameAs(ok);
    assertThat(request.getHeaders().getFirst("X-Riot-Token")).isEqualTo("key-a");
    verify(keyLease).acquire();
  }

  @Test
  void throwsRiotRateLimitedExceptionOnTooManyRequests() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse tooMany = mock(ClientHttpResponse.class);
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("Retry-After", "10");
    when(tooMany.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
    when(tooMany.getHeaders()).thenReturn(headers);
    when(keyPool.leaseForRequest()).thenReturn(keyLease);
    when(keyLease.apiKey()).thenReturn("key-a");
    when(execution.execute(any(), any())).thenReturn(tooMany);

    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RiotRateLimitedException.class)
        .hasMessageContaining("retry-after=10")
        .hasMessageContaining("uri=" + request.getURI());

    verify(keyLease).acquire();
    verify(keyLease).markRateLimited(Duration.ofSeconds(10));
    verify(tooMany).close();
  }

  @Test
  void doesNotMarkRateLimitedWhenRequestSucceeds() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse ok = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    when(keyPool.leaseForRequest()).thenReturn(keyLease);
    when(keyLease.apiKey()).thenReturn("key-a");
    when(execution.execute(any(), any())).thenReturn(ok);

    interceptor.intercept(request, new byte[0], execution);

    verify(keyLease, never()).markRateLimited(any());
  }
}
