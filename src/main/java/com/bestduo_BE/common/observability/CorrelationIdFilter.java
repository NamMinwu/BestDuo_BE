package com.bestduo_BE.common.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String requestId = resolveRequestId(request);
    MDC.put(MDC_KEY, requestId);
    try {
      if (response instanceof HttpServletResponse httpResponse) {
        httpResponse.setHeader(HEADER_NAME, requestId);
      }
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String resolveRequestId(ServletRequest request) {
    if (request instanceof HttpServletRequest httpRequest) {
      String fromHeader = httpRequest.getHeader(HEADER_NAME);
      if (fromHeader != null && !fromHeader.isBlank()) {
        return fromHeader;
      }
    }
    return UUID.randomUUID().toString();
  }
}
