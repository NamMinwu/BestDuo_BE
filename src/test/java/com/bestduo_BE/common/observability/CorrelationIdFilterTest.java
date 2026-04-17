package com.bestduo_BE.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  @DisplayName("헤더가 없으면 UUID를 생성해 응답 헤더와 MDC에 세팅한다")
  void whenHeaderMissing_generatesUuid() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] mdcDuringChain = new String[1];

    FilterChain chain = (req, res) -> mdcDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

    filter.doFilter(request, response, chain);

    String headerValue = response.getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(headerValue).isNotBlank();
    assertThat(mdcDuringChain[0]).isEqualTo(headerValue);
  }

  @Test
  @DisplayName("X-Request-Id 헤더가 있으면 그 값을 그대로 사용한다")
  void whenHeaderPresent_reusesHeaderValue() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] mdcDuringChain = new String[1];

    FilterChain chain = (req, res) -> mdcDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123");
    assertThat(mdcDuringChain[0]).isEqualTo("abc-123");
  }

  @Test
  @DisplayName("요청 종료 후 MDC에서 correlationId 키가 제거된다")
  void afterFilter_mdcIsCleared() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("체인이 예외를 던져도 MDC는 정리된다")
  void whenChainThrows_mdcStillCleared() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      throw new ServletException("boom");
    };

    try {
      filter.doFilter(request, response, chain);
    } catch (Exception ignored) {
    }

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }
}
